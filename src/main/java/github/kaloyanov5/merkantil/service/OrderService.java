package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.alpaca.AlpacaSnapshot;
import github.kaloyanov5.merkantil.dto.request.OrderRequest;
import github.kaloyanov5.merkantil.dto.response.OrderResponse;
import github.kaloyanov5.merkantil.entity.*;
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
    private final AlpacaApiService alpacaApiService;

    /**
     * Place a new order (BUY or SELL)
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest request) {
        // Validate user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validate stock exists
        Stock stock = stockRepository.findBySymbol(request.getSymbol().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + request.getSymbol()));

        if (!stock.getIsActive()) {
            throw new IllegalArgumentException("Stock is not active for trading");
        }

        // Get current market price
        Double executionPrice = getExecutionPrice(request, stock);

        // Validate order based on side
        Side side = Side.valueOf(request.getSide().toUpperCase());
        if (side == Side.BUY) {
            return executeBuyOrder(user, stock, request, executionPrice);
        } else {
            return executeSellOrder(user, stock, request, executionPrice);
        }
    }

    /**
     * Execute BUY order
     */
    private OrderResponse executeBuyOrder(User user, Stock stock, OrderRequest request, Double executionPrice) {
        Double totalCost = executionPrice * request.getQuantity();

        // Check if user has sufficient funds
        if (user.getBalance() < totalCost) {
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
        order.setOrderType(OrderType.valueOf(request.getOrderType().toUpperCase()));
        Order savedOrder = orderRepository.save(order);

        // Debit user balance
        user.setBalance(user.getBalance() - totalCost);
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

        return mapToOrderResponse(savedOrder, executionPrice, totalCost, "FILLED");
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

        // Create order
        Order order = new Order();
        order.setUser(user);
        order.setSymbol(stock.getSymbol());
        order.setType(Side.SELL);
        order.setQuantity(request.getQuantity());
        order.setAtPrice(executionPrice);
        order.setOrderType(OrderType.valueOf(request.getOrderType().toUpperCase()));
        Order savedOrder = orderRepository.save(order);

        // Credit user balance
        user.setBalance(user.getBalance() + totalRevenue);
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

        return mapToOrderResponse(savedOrder, executionPrice, totalRevenue, "FILLED");
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
        Integer remainingQuantity = portfolio.getQuantity() - quantity;

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
     * Get execution price based on order type
     */
    private Double getExecutionPrice(OrderRequest request, Stock stock) {
        OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());

        if (orderType == OrderType.MARKET) {
            // Get current market price from Alpaca
            AlpacaSnapshot snapshot = alpacaApiService.getSnapshot(stock.getSymbol());
            if (snapshot == null || snapshot.getLatestTrade() == null) {
                throw new IllegalStateException("Unable to fetch current price for " + stock.getSymbol());
            }
            return snapshot.getLatestTrade().getPrice();
        } else {
            // LIMIT order - use specified limit price
            if (request.getLimitPrice() == null || request.getLimitPrice() <= 0) {
                throw new IllegalArgumentException("Limit price is required for LIMIT orders");
            }
            return request.getLimitPrice();
        }
    }

    /**
     * Get user's order history
     */
    public Page<OrderResponse> getUserOrders(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Order> orders = orderRepository.findByUserId(userId, pageable);

        return orders.map(order -> mapToOrderResponse(
                order,
                order.getAtPrice(),
                order.getAtPrice() * order.getQuantity(),
                "FILLED"
        ));
    }

    /**
     * Get orders by symbol
     */
    public Page<OrderResponse> getUserOrdersBySymbol(Long userId, String symbol, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Order> ordersBySymbol = orderRepository.findByUserIdAndSymbol(userId, symbol, pageable);

        return ordersBySymbol.map(order -> mapToOrderResponse(
                order,
                order.getAtPrice(),
                order.getAtPrice() * order.getQuantity(),
                "FILLED"
        ));
    }

    private OrderResponse mapToOrderResponse(Order order, Double executedPrice, Double totalAmount, String status) {
        return new OrderResponse(
                order.getId(),
                order.getSymbol(),
                order.getType().name(),
                order.getQuantity(),
                executedPrice,
                totalAmount,
                order.getOrderType().name(),
                status,
                order.getTimestamp()
        );
    }
}