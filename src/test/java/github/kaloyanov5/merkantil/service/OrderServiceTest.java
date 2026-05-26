package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveLastTrade;
import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.dto.request.OrderRequest;
import github.kaloyanov5.merkantil.dto.response.OrderResponse;
import github.kaloyanov5.merkantil.entity.*;
import github.kaloyanov5.merkantil.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private StockRepository stockRepository;
    @Mock private UserRepository userRepository;
    @Mock private MassiveApiService massiveApiService;
    @Mock private EmailService emailService;
    @Mock private RateLimiterService rateLimiterService;
    @Mock private MarketSessionService marketSessionService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setBalance(new BigDecimal("10000.00"));
        user.setBanned(false);
        user.setRole(Role.USER);

        stock = new Stock();
        stock.setSymbol("AAPL");
        stock.setName("Apple Inc.");
        stock.setIsActive(true);
        stock.setCurrentPrice(new BigDecimal("150.00"));

        // Default stubs — referenced by most tests
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(stock));
        // orderRepository.save / transactionRepository.save / portfolioRepository.save
        // return the same object passed in — needed to set an id-less Order/Portfolio back to caller
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        // Default to regular trading hours so MARKET orders pass the session gate
        when(marketSessionService.getCurrentSession()).thenReturn("OPEN");
    }

    private OrderRequest marketOrder(String side, int qty) {
        return new OrderRequest("AAPL", side, qty, "MARKET", null);
    }

    private OrderRequest limitOrder(String side, int qty, double limit) {
        return new OrderRequest("AAPL", side, qty, "LIMIT", BigDecimal.valueOf(limit));
    }

    private void stubMarketPrice(double price) {
        MassiveLastTrade lastTrade = new MassiveLastTrade(price, null, null, null, null, null);
        MassiveSnapshotTicker snapshot = new MassiveSnapshotTicker(
                "AAPL", null, null, null, lastTrade, null, null, null, null, null);
        when(massiveApiService.getSnapshot("AAPL")).thenReturn(snapshot);
    }

    // ---------- MARKET BUY ----------

    @Test
    @DisplayName("market BUY: debits balance and creates new portfolio position")
    void marketBuy_success_createsNewPosition() {
        stubMarketPrice(150.0);
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.empty());

        OrderResponse response = orderService.placeOrder(1L, marketOrder("BUY", 10));

        assertThat(response.status()).isEqualTo("FILLED");
        assertThat(response.executedPrice()).isEqualByComparingTo("150.00");
        assertThat(response.quantity()).isEqualTo(10);
        // 10000 - (150 * 10) = 8500
        assertThat(user.getBalance()).isEqualByComparingTo("8500.00");

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        Portfolio saved = portfolioCaptor.getValue();
        assertThat(saved.getQuantity()).isEqualTo(10);
        assertThat(saved.getAverageBuyPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("market BUY: throws when balance is insufficient")
    void marketBuy_insufficientFunds_throws() {
        stubMarketPrice(150.0);
        user.setBalance(new BigDecimal("100.00"));

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("BUY", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        verify(orderRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("market BUY twice: recomputes weighted average buy price")
    void marketBuy_secondBuy_updatesAveragePrice() {
        Portfolio existing = new Portfolio();
        existing.setId(99L);
        existing.setUser(user);
        existing.setSymbol("AAPL");
        existing.setQuantity(10);
        existing.setAverageBuyPrice(new BigDecimal("100.00"));

        stubMarketPrice(200.0);
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.of(existing));

        orderService.placeOrder(1L, marketOrder("BUY", 10));

        // Weighted average: (10*100 + 10*200) / 20 = 150
        assertThat(existing.getQuantity()).isEqualTo(20);
        assertThat(existing.getAverageBuyPrice()).isEqualByComparingTo("150.00");
    }

    // ---------- MARKET SELL ----------

    @Test
    @DisplayName("market SELL: credits balance and reduces portfolio quantity")
    void marketSell_partial_reducesQuantity() {
        Portfolio existing = new Portfolio();
        existing.setId(99L);
        existing.setUser(user);
        existing.setSymbol("AAPL");
        existing.setQuantity(10);
        existing.setAverageBuyPrice(new BigDecimal("100.00"));

        stubMarketPrice(150.0);
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.placeOrder(1L, marketOrder("SELL", 4));

        assertThat(response.status()).isEqualTo("FILLED");
        // 10000 + 4 * 150 = 10600
        assertThat(user.getBalance()).isEqualByComparingTo("10600.00");
        assertThat(existing.getQuantity()).isEqualTo(6);
        verify(portfolioRepository, never()).delete(any());
    }

    @Test
    @DisplayName("market SELL all shares: closes portfolio position")
    void marketSell_full_closesPosition() {
        Portfolio existing = new Portfolio();
        existing.setId(99L);
        existing.setUser(user);
        existing.setSymbol("AAPL");
        existing.setQuantity(5);
        existing.setAverageBuyPrice(new BigDecimal("100.00"));

        stubMarketPrice(150.0);
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.of(existing));

        orderService.placeOrder(1L, marketOrder("SELL", 5));

        verify(portfolioRepository).delete(existing);
    }

    @Test
    @DisplayName("market SELL: throws when user owns fewer shares than requested")
    void marketSell_insufficientShares_throws() {
        Portfolio existing = new Portfolio();
        existing.setId(99L);
        existing.setUser(user);
        existing.setSymbol("AAPL");
        existing.setQuantity(2);
        existing.setAverageBuyPrice(new BigDecimal("100.00"));

        stubMarketPrice(150.0);
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("SELL", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient shares");
    }

    @Test
    @DisplayName("market SELL: throws when user owns no shares")
    void marketSell_noPosition_throws() {
        stubMarketPrice(150.0);
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("SELL", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("don't own");
    }

    // ---------- VALIDATION ----------

    @Test
    @DisplayName("banned user: rejected before any market call")
    void bannedUser_rejected() {
        user.setBanned(true);

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("BUY", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suspended");

        verifyNoInteractions(massiveApiService);
    }

    @Test
    @DisplayName("inactive stock: order rejected")
    void inactiveStock_rejected() {
        stock.setIsActive(false);

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("BUY", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("unknown user id: throws")
    void unknownUser_throws() {
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(99L, marketOrder("BUY", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("market order: rejected when the market is not in regular trading hours")
    void marketOrder_marketClosed_rejected() {
        when(marketSessionService.getCurrentSession()).thenReturn("CLOSED");

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("BUY", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regular trading hours");

        verify(orderRepository, never()).save(any());
        verify(massiveApiService, never()).getSnapshot(any());
    }

    @Test
    @DisplayName("market order: rejected during pre-market with a session-specific message")
    void marketOrder_preMarket_rejected() {
        when(marketSessionService.getCurrentSession()).thenReturn("PRE_MARKET");

        assertThatThrownBy(() -> orderService.placeOrder(1L, marketOrder("BUY", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pre-market");
    }

    // ---------- LIMIT ORDERS ----------

    @Test
    @DisplayName("LIMIT BUY: reserves funds and creates OPEN order")
    void limitBuy_reservesFundsAndOpensOrder() {
        OrderResponse response = orderService.placeOrder(1L, limitOrder("BUY", 10, 140.0));

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.orderType()).isEqualTo("LIMIT");
        // 10000 - 10 * 140 = 8600 reserved
        assertThat(user.getBalance()).isEqualByComparingTo("8600.00");
        verify(massiveApiService, never()).getSnapshot(any());
    }

    @Test
    @DisplayName("LIMIT order: still accepted when the market is closed")
    void limitOrder_marketClosed_stillAllowed() {
        when(marketSessionService.getCurrentSession()).thenReturn("CLOSED");

        OrderResponse response = orderService.placeOrder(1L, limitOrder("BUY", 10, 140.0));

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.orderType()).isEqualTo("LIMIT");
    }

    @Test
    @DisplayName("LIMIT BUY: rejected when reserved cost exceeds balance")
    void limitBuy_insufficientFunds_throws() {
        user.setBalance(new BigDecimal("100.00"));

        assertThatThrownBy(() -> orderService.placeOrder(1L, limitOrder("BUY", 10, 140.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("LIMIT BUY: rejected when limitPrice missing")
    void limitBuy_missingLimitPrice_throws() {
        OrderRequest req = new OrderRequest("AAPL", "BUY", 10, "LIMIT", null);

        assertThatThrownBy(() -> orderService.placeOrder(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit price is required");
    }

    @Test
    @DisplayName("LIMIT SELL: requires existing position with enough shares")
    void limitSell_insufficientShares_throws() {
        Portfolio existing = new Portfolio();
        existing.setId(99L);
        existing.setUser(user);
        existing.setSymbol("AAPL");
        existing.setQuantity(2);
        existing.setAverageBuyPrice(new BigDecimal("100.00"));
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.placeOrder(1L, limitOrder("SELL", 5, 200.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient shares");
    }

    @Test
    @DisplayName("cancelOrder: refunds reserved funds for LIMIT BUY")
    void cancelOrder_limitBuy_refundsReservedFunds() {
        // Place limit buy first to reserve $1400
        orderService.placeOrder(1L, limitOrder("BUY", 10, 140.0));
        assertThat(user.getBalance()).isEqualByComparingTo("8600.00");

        // Construct the placed order for cancellation
        Order placed = new Order();
        placed.setId(42L);
        placed.setUser(user);
        placed.setSymbol("AAPL");
        placed.setType(Side.BUY);
        placed.setQuantity(10);
        placed.setLimitPrice(BigDecimal.valueOf(140.0));
        placed.setOrderType(OrderType.LIMIT);
        placed.setStatus(OrderStatus.OPEN);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(placed));

        OrderResponse cancelled = orderService.cancelOrder(1L, 42L);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        // Reserved funds returned: balance back to 10000
        assertThat(user.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("cancelOrder: rejects when order belongs to a different user")
    void cancelOrder_otherUser_rejected() {
        User otherUser = new User();
        otherUser.setId(2L);

        Order other = new Order();
        other.setId(42L);
        other.setUser(otherUser);
        other.setStatus(OrderStatus.OPEN);
        other.setOrderType(OrderType.LIMIT);
        other.setType(Side.BUY);
        other.setQuantity(10);
        other.setLimitPrice(BigDecimal.valueOf(140.0));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to you");
    }

    @Test
    @DisplayName("cancelOrder: only OPEN orders can be cancelled")
    void cancelOrder_filledOrder_rejected() {
        Order filled = new Order();
        filled.setId(42L);
        filled.setUser(user);
        filled.setStatus(OrderStatus.FILLED);
        filled.setOrderType(OrderType.LIMIT);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(filled));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open orders");
    }

    @Test
    @DisplayName("executeLimitOrder BUY: refunds the difference when filled below limit")
    void executeLimitOrder_buy_refundsDifferenceUnderLimit() {
        // User started at 10000, suppose 1400 was reserved earlier — represent that here
        user.setBalance(new BigDecimal("8600.00"));

        Order order = new Order();
        order.setId(50L);
        order.setUser(user);
        order.setSymbol("AAPL");
        order.setType(Side.BUY);
        order.setQuantity(10);
        order.setLimitPrice(BigDecimal.valueOf(140.0)); // reserved 1400
        order.setOrderType(OrderType.LIMIT);
        order.setStatus(OrderStatus.OPEN);

        when(orderRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(portfolioRepository.findByUserIdAndSymbolForUpdate(1L, "AAPL")).thenReturn(Optional.empty());

        // Filled at 130 → actual cost 1300 → refund 100
        orderService.executeLimitOrder(50L, new BigDecimal("130.0"));

        // 8600 + 100 refund = 8700
        assertThat(user.getBalance()).isEqualByComparingTo("8700.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getAtPrice()).isEqualByComparingTo("130.00");
    }
}
