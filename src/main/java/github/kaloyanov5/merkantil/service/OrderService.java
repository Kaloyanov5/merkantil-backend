package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.dto.request.OrderRequest;
import github.kaloyanov5.merkantil.dto.response.OrderResponse;
import github.kaloyanov5.merkantil.entity.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import github.kaloyanov5.merkantil.repository.*;
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

    /**
     * Place a new order (BUY or SELL)
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest request) {
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
            if (request.getLimitPrice() == null || request.getLimitPrice() <= 0) {
                throw new IllegalArgumentException("Limit price is required for LIMIT orders");
            }
            return side == Side.BUY
                    ? placeLimitBuyOrder(user, stock, request)
                    : placeLimitSellOrder(user, stock, request);
        }

        Double executionPrice = getMarketPrice(stock);
        return side == Side.BUY
                ? executeBuyOrder(user, stock, request, executionPrice)
                : executeSellOrder(user, stock, request, executionPrice);
    }

    /**
     * Place a LIMIT BUY order — reserves funds immediately, waits for price condition.
     */
    private OrderResponse placeLimitBuyOrder(User user, Stock stock, OrderRequest request) {
        Double reserved = request.getLimitPrice() * request.getQuantity();
        BigDecimal reservedDecimal = BigDecimal.valueOf(reserved);

        if (user.getBalance().compareTo(reservedDecimal) < 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient funds. Required: $%.2f, Available: $%.2f",
                            reserved, user.getBalance()));
        }

        user.setBalance(user.getBalance().subtract(reservedDecimal));
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
    public void executeLimitOrder(Order order, Double executionPrice) {
        User user = order.getUser();

        if (order.getType() == Side.BUY) {
            // Funds already reserved — just update portfolio and create transaction
            updatePortfolioAfterBuy(user, order.getSymbol(), order.getQuantity(), executionPrice);

            // Refund the difference if executed below limit price
            Double reserved = order.getLimitPrice() * order.getQuantity();
            Double actualCost = executionPrice * order.getQuantity();
            double refund = reserved - actualCost;
            if (refund > 0) {
                user.setBalance(user.getBalance().add(BigDecimal.valueOf(refund)));
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
            Double revenue = executionPrice * order.getQuantity();
            user.setBalance(user.getBalance().add(BigDecimal.valueOf(revenue)));
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

        double totalValue = executionPrice * order.getQuantity();
        double refund = order.getType() == Side.BUY
                ? Math.max(0, order.getLimitPrice() * order.getQuantity() - totalValue)
                : 0;
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
            Double refund = order.getLimitPrice() * order.getQuantity();
            User user = order.getUser();
            user.setBalance(user.getBalance().add(BigDecimal.valueOf(refund)));
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
    private OrderResponse executeBuyOrder(User user, Stock stock, OrderRequest request, Double executionPrice) {
        Double totalCost = executionPrice * request.getQuantity();
        BigDecimal totalCostDecimal = BigDecimal.valueOf(totalCost);

        // Check if user has sufficient funds
        if (user.getBalance().compareTo(totalCostDecimal) < 0) {
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
        user.setBalance(user.getBalance().subtract(totalCostDecimal));
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
    private OrderResponse executeSellOrder(User user, Stock stock, OrderRequest request, Double executionPrice) {
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

        Double totalRevenue = executionPrice * request.getQuantity();
        BigDecimal totalRevenueDecimal = BigDecimal.valueOf(totalRevenue);

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
        user.setBalance(user.getBalance().add(totalRevenueDecimal));
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
    private void updatePortfolioAfterBuy(User user, String symbol, Integer quantity, Double price) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndSymbol(user.getId(), symbol)
                .orElse(new Portfolio());

        if (portfolio.getId() == null) {
            // New position
            portfolio.setUser(user);
            portfolio.setSymbol(symbol);
            portfolio.setQuantity(quantity);
            portfolio.setAverageBuyPrice(price);
        } else {
            // Update existing position (calculate new average)
            Integer totalQuantity = portfolio.getQuantity() + quantity;
            Double totalCost = (portfolio.getQuantity() * portfolio.getAverageBuyPrice()) + (quantity * price);
            portfolio.setAverageBuyPrice(totalCost / totalQuantity);
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
    private Double getMarketPrice(Stock stock) {
        MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(stock.getSymbol());

        if (snapshot != null) {
            if (snapshot.getLastTrade() != null && snapshot.getLastTrade().getPrice() != null
                    && snapshot.getLastTrade().getPrice() > 0) {
                return snapshot.getLastTrade().getPrice();
            }
            if (snapshot.getDay() != null && snapshot.getDay().getClose() != null
                    && snapshot.getDay().getClose() > 0) {
                return snapshot.getDay().getClose();
            }
        }

        if (stock.getCurrentPrice() != null && stock.getCurrentPrice() > 0) {
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
        Double total = order.getAtPrice() != null ? order.getAtPrice() * order.getQuantity() : null;
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
