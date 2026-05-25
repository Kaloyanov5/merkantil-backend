package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.dto.request.OrderRequest;
import github.kaloyanov5.merkantil.dto.response.OrderResponse;
import github.kaloyanov5.merkantil.entity.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import github.kaloyanov5.merkantil.repository.*;
import github.kaloyanov5.merkantil.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final MassiveApiService massiveApiService;
    private final EmailService emailService;
    private final RateLimiterService rateLimiterService;
    private final MarketSessionService marketSessionService;

    /** Maximum order placements allowed per user within {@link #ORDER_RATE_WINDOW}. */
    private static final int MAX_ORDERS_PER_WINDOW = 10;
    private static final Duration ORDER_RATE_WINDOW = Duration.ofMinutes(1);

    /**
     * Place a new order (BUY or SELL)
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest request) {
        // Throttle order placement per user to block automated bursts
        rateLimiterService.enforce("order:" + userId, MAX_ORDERS_PER_WINDOW, ORDER_RATE_WINDOW);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new IllegalArgumentException("Your account has been suspended");
        }

        Stock stock = stockRepository.findBySymbol(request.getSymbol().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + request.getSymbol()));

        if (!stock.getIsActive()) {
            throw new IllegalArgumentException("Stock is not active for trading");
        }

        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());
        Side side = Side.valueOf(request.getSide().toUpperCase());

        if (orderType == OrderType.LIMIT) {
            if (request.getLimitPrice() == null || request.getLimitPrice().signum() <= 0) {
                throw new IllegalArgumentException("Limit price is required for LIMIT orders");
            }
            return side == Side.BUY
                    ? placeLimitBuyOrder(user, stock, request)
                    : placeLimitSellOrder(user, stock, request);
        }

        // MARKET orders execute immediately, so they are only valid during
        // regular trading hours. LIMIT orders are exempt — they are placed in
        // any session and queue until their price condition is met.
        String session = marketSessionService.getCurrentSession();
        if (!"OPEN".equals(session)) {
            throw new IllegalArgumentException(
                    "The market is currently " + describeSession(session) + ". Market orders can only be "
                            + "placed during regular trading hours (9:30 AM - 4:00 PM ET). Place a limit order instead.");
        }

        BigDecimal executionPrice = getMarketPrice(stock);
        return side == Side.BUY
                ? executeBuyOrder(user, stock, request, executionPrice)
                : executeSellOrder(user, stock, request, executionPrice);
    }

    /** Human-readable description of a non-OPEN market session for error messages. */
    private String describeSession(String session) {
        return switch (session) {
            case "PRE_MARKET" -> "in pre-market";
            case "AFTER_HOURS" -> "in after-hours";
            case "HOLIDAY" -> "closed for a holiday";
            default -> "closed";
        };
    }

    /**
     * Place a LIMIT BUY order — reserves funds immediately, waits for price condition.
     */
    private OrderResponse placeLimitBuyOrder(User user, Stock stock, OrderRequest request) {
        BigDecimal reserved = MoneyUtil.multiply(request.getLimitPrice(), request.getQuantity());

        if (user.getBalance().compareTo(reserved) < 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient funds. Required: $%.2f, Available: $%.2f",
                            reserved, user.getBalance()));
        }

        user.setBalance(user.getBalance().subtract(reserved));
        userRepository.save(user);

        Order order = new Order();
        order.setUser(user);
        order.setSymbol(stock.getSymbol());
        order.setType(Side.BUY);
        order.setQuantity(request.getQuantity());
        order.setLimitPrice(request.getLimitPrice());
        order.setOrderType(OrderType.LIMIT);
        order.setStatus(OrderStatus.OPEN);
        Order saved = orderRepository.save(order);

        log.info("LIMIT BUY placed: User {} wants {} shares of {} at ${} (funds reserved)",
                user.getId(), request.getQuantity(), stock.getSymbol(), request.getLimitPrice());
        return mapToOrderResponse(saved);
    }

    /**
     * Place a LIMIT SELL order — validates shares exist, waits for price condition.
     */
    private OrderResponse placeLimitSellOrder(User user, Stock stock, OrderRequest request) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndSymbol(user.getId(), stock.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException("You don't own any shares of " + stock.getSymbol()));

        if (portfolio.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    String.format("Insufficient shares. You own %d but tried to sell %d",
                            portfolio.getQuantity(), request.getQuantity()));
        }

        Order order = new Order();
        order.setUser(user);
        order.setSymbol(stock.getSymbol());
        order.setType(Side.SELL);
        order.setQuantity(request.getQuantity());
        order.setLimitPrice(request.getLimitPrice());
        order.setOrderType(OrderType.LIMIT);
        order.setStatus(OrderStatus.OPEN);
        Order saved = orderRepository.save(order);

        log.info("LIMIT SELL placed: User {} wants to sell {} shares of {} at ${}",
                user.getId(), request.getQuantity(), stock.getSymbol(), request.getLimitPrice());
        return mapToOrderResponse(saved);
    }

    /**
     * Execute an open LIMIT order — called by the scheduler when price condition is met.
     */
    @Transactional
    public void executeLimitOrder(Order order, BigDecimal executionPrice) {
        User user = order.getUser();

        if (order.getType() == Side.BUY) {
            // Funds already reserved — just update portfolio and create transaction
            updatePortfolioAfterBuy(user, order.getSymbol(), order.getQuantity(), executionPrice);

            // Refund the difference if executed below limit price
            BigDecimal reserved = MoneyUtil.multiply(order.getLimitPrice(), order.getQuantity());
            BigDecimal actualCost = MoneyUtil.multiply(executionPrice, order.getQuantity());
            BigDecimal refund = reserved.subtract(actualCost);
            if (refund.signum() > 0) {
                user.setBalance(user.getBalance().add(refund));
                userRepository.save(user);
            }
        } else {
            // SELL: check shares still exist (may have been sold manually)
            Portfolio portfolio = portfolioRepository.findByUserIdAndSymbol(user.getId(), order.getSymbol())
                    .orElse(null);
            if (portfolio == null || portfolio.getQuantity() < order.getQuantity()) {
                log.warn("LIMIT SELL order {} cancelled — user no longer has enough shares", order.getId());
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                return;
            }
            BigDecimal revenue = MoneyUtil.multiply(executionPrice, order.getQuantity());
            user.setBalance(user.getBalance().add(revenue));
            userRepository.save(user);
            updatePortfolioAfterSell(user, portfolio, order.getQuantity());
        }

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(order);
        transaction.setStockSymbol(order.getSymbol());
        transaction.setType(order.getType());
        transaction.setQuantity(order.getQuantity());
        transaction.setPrice(executionPrice);
        transactionRepository.save(transaction);

        order.setAtPrice(executionPrice);
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        log.info("LIMIT {} order {} filled: {} shares of {} at ${}",
                order.getType(), order.getId(), order.getQuantity(), order.getSymbol(), executionPrice);

        BigDecimal totalValue = MoneyUtil.multiply(executionPrice, order.getQuantity());
        BigDecimal refund = order.getType() == Side.BUY
                ? MoneyUtil.multiply(order.getLimitPrice(), order.getQuantity())
                        .subtract(totalValue).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        try {
            emailService.sendLimitOrderFilledEmail(
                    user.getEmail(),
                    order.getType().name(),
                    order.getSymbol(),
                    order.getQuantity(),
                    executionPrice,
                    totalValue,
                    refund
            );
        } catch (Exception e) {
            log.error("Failed to send order fill email for order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Cancel an open LIMIT order. Refunds reserved funds for BUY orders.
     */
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Order does not belong to you");
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new IllegalArgumentException("Only open orders can be cancelled");
        }
        if (order.getOrderType() != OrderType.LIMIT) {
            throw new IllegalArgumentException("Only limit orders can be cancelled");
        }

        // Refund reserved funds for BUY orders
        if (order.getType() == Side.BUY) {
            BigDecimal refund = MoneyUtil.multiply(order.getLimitPrice(), order.getQuantity());
            User user = order.getUser();
            user.setBalance(user.getBalance().add(refund));
            userRepository.save(user);
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("LIMIT order {} cancelled by user {}", orderId, userId);
        return mapToOrderResponse(order);
    }

    /**
     * Execute BUY order
     */
    private OrderResponse executeBuyOrder(User user, Stock stock, OrderRequest request, BigDecimal executionPrice) {
        BigDecimal totalCost = MoneyUtil.multiply(executionPrice, request.getQuantity());

        // Check if user has sufficient funds
        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient funds. Required: $%.2f, Available: $%.2f",
                            totalCost, user.getBalance())
            );
        }

        // Create order
        Order order = new Order();
        order.setUser(user);
        order.setSymbol(stock.getSymbol());
        order.setType(Side.BUY);
        order.setQuantity(request.getQuantity());
        order.setAtPrice(executionPrice);
        order.setOrderType(OrderType.MARKET);
        order.setStatus(OrderStatus.FILLED);
        Order savedOrder = orderRepository.save(order);

        // Debit user balance
        user.setBalance(user.getBalance().subtract(totalCost));
        userRepository.save(user);

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(savedOrder);
        transaction.setStockSymbol(stock.getSymbol());
        transaction.setType(Side.BUY);
        transaction.setQuantity(request.getQuantity());
        transaction.setPrice(executionPrice);
        transactionRepository.save(transaction);

        // Update portfolio
        updatePortfolioAfterBuy(user, stock.getSymbol(), request.getQuantity(), executionPrice);

        log.info("BUY order executed: User {} bought {} shares of {} at ${} (Total: ${})",
                user.getId(), request.getQuantity(), stock.getSymbol(), executionPrice, totalCost);

        return mapToOrderResponse(savedOrder);
    }

    /**
     * Execute SELL order
     */
    private OrderResponse executeSellOrder(User user, Stock stock, OrderRequest request, BigDecimal executionPrice) {
        // Check if user owns the stock
        Portfolio portfolio = portfolioRepository.findByUserIdAndSymbol(user.getId(), stock.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException("You don't own any shares of " + stock.getSymbol()));

        // Check if user has enough shares
        if (portfolio.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    String.format("Insufficient shares. You own %d shares but trying to sell %d",
                            portfolio.getQuantity(), request.getQuantity())
            );
        }

        BigDecimal totalRevenue = MoneyUtil.multiply(executionPrice, request.getQuantity());

        // Create order
        Order order = new Order();
        order.setUser(user);
        order.setSymbol(stock.getSymbol());
        order.setType(Side.SELL);
        order.setQuantity(request.getQuantity());
        order.setAtPrice(executionPrice);
        order.setOrderType(OrderType.MARKET);
        order.setStatus(OrderStatus.FILLED);
        Order savedOrder = orderRepository.save(order);

        // Credit user balance
        user.setBalance(user.getBalance().add(totalRevenue));
        userRepository.save(user);

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setOrder(savedOrder);
        transaction.setStockSymbol(stock.getSymbol());
        transaction.setType(Side.SELL);
        transaction.setQuantity(request.getQuantity());
        transaction.setPrice(executionPrice);
        transactionRepository.save(transaction);

        // Update portfolio
        updatePortfolioAfterSell(user, portfolio, request.getQuantity());

        log.info("SELL order executed: User {} sold {} shares of {} at ${} (Total: ${})",
                user.getId(), request.getQuantity(), stock.getSymbol(), executionPrice, totalRevenue);

        return mapToOrderResponse(savedOrder);
    }

    /**
     * Update portfolio after BUY
     */
    private void updatePortfolioAfterBuy(User user, String symbol, Integer quantity, BigDecimal price) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndSymbol(user.getId(), symbol)
                .orElse(new Portfolio());

        if (portfolio.getId() == null) {
            // New position
            portfolio.setUser(user);
            portfolio.setSymbol(symbol);
            portfolio.setQuantity(quantity);
            portfolio.setAverageBuyPrice(MoneyUtil.scaled(price));
        } else {
            // Update existing position — recompute the weighted-average buy price
            int totalQuantity = portfolio.getQuantity() + quantity;
            BigDecimal existingCost = MoneyUtil.multiply(portfolio.getAverageBuyPrice(), portfolio.getQuantity());
            BigDecimal addedCost = MoneyUtil.multiply(price, quantity);
            BigDecimal newAverage = MoneyUtil.divide(
                    existingCost.add(addedCost), BigDecimal.valueOf(totalQuantity));
            portfolio.setAverageBuyPrice(newAverage);
            portfolio.setQuantity(totalQuantity);
        }

        portfolioRepository.save(portfolio);
    }

    /**
     * Update portfolio after SELL
     */
    private void updatePortfolioAfterSell(User user, Portfolio portfolio, Integer quantity) {
        int remainingQuantity = portfolio.getQuantity() - quantity;

        if (remainingQuantity == 0) {
            // Sold all shares, remove position
            portfolioRepository.delete(portfolio);
            log.info("Portfolio position closed for user {} - {}", user.getId(), portfolio.getSymbol());
        } else {
            // Update remaining quantity (average price stays the same)
            portfolio.setQuantity(remainingQuantity);
            portfolioRepository.save(portfolio);
        }
    }

    /**
     * Get current market price for MARKET orders.
     */
    private BigDecimal getMarketPrice(Stock stock) {
        MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(stock.getSymbol());

        if (snapshot != null) {
            if (snapshot.getLastTrade() != null && snapshot.getLastTrade().getPrice() != null
                    && snapshot.getLastTrade().getPrice() > 0) {
                return MoneyUtil.of(snapshot.getLastTrade().getPrice());
            }
            if (snapshot.getDay() != null && snapshot.getDay().getClose() != null
                    && snapshot.getDay().getClose() > 0) {
                return MoneyUtil.of(snapshot.getDay().getClose());
            }
        }

        if (MoneyUtil.isPositive(stock.getCurrentPrice())) {
            log.warn("Using DB price for {} — snapshot unavailable", stock.getSymbol());
            return stock.getCurrentPrice();
        }

        throw new IllegalArgumentException("Unable to determine market price for " + stock.getSymbol() + ". Please try again shortly.");
    }

    /**
     * Get user's order history
     */
    public Page<OrderResponse> getUserOrders(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"));
        return orderRepository.findByUserId(userId, pageable).map(this::mapToOrderResponse);
    }

    /**
     * Get orders by symbol
     */
    public Page<OrderResponse> getUserOrdersBySymbol(Long userId, String symbol, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"));
        return orderRepository.findByUserIdAndSymbol(userId, symbol, pageable).map(this::mapToOrderResponse);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        BigDecimal total = order.getAtPrice() != null
                ? MoneyUtil.multiply(order.getAtPrice(), order.getQuantity()) : null;
        return new OrderResponse(
                order.getId(),
                order.getSymbol(),
                order.getType().name(),
                order.getQuantity(),
                order.getAtPrice(),
                order.getLimitPrice(),
                total,
                order.getOrderType().name(),
                order.getStatus().name(),
                order.getTimestamp()
        );
    }
}
