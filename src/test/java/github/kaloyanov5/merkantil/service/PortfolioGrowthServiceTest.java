package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.response.PortfolioGrowthResponse;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioGrowthServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private StockPriceHistoryRepository stockPriceHistoryRepository;
    @Mock private MarketCalendar marketCalendar;

    private PortfolioGrowthService service;

    // Five consecutive weekdays
    private static final LocalDate DAY_1 = LocalDate.of(2026, 5, 4);  // Mon
    private static final LocalDate DAY_2 = LocalDate.of(2026, 5, 5);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 5, 6);
    private static final LocalDate DAY_4 = LocalDate.of(2026, 5, 7);
    private static final LocalDate DAY_5 = LocalDate.of(2026, 5, 8);  // Fri

    private final List<Transaction> allTransactions = new ArrayList<>();
    private final Map<String, Map<LocalDate, BigDecimal>> priceHistory = new HashMap<>();

    @BeforeEach
    void setUp() {
        allTransactions.clear();
        priceHistory.clear();

        // Mark every test day as a trading day
        when(marketCalendar.isTradingDay(any(LocalDate.class))).thenReturn(true);

        // Transactions stub: return all transactions dated on or before the requested date
        when(transactionRepository.findByUserIdAndDateBeforeOrEqual(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate date = inv.getArgument(1);
                    return allTransactions.stream()
                            .filter(tx -> !tx.getTimestamp().toLocalDate().isAfter(date))
                            .toList();
                });

        // Historical price stub: return the most recent price on or before the requested date
        when(stockPriceHistoryRepository.findMostRecentPriceOnOrBefore(any(String.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    String symbol = ((String) inv.getArgument(0)).toUpperCase();
                    LocalDate date = inv.getArgument(1);
                    Map<LocalDate, BigDecimal> bySymbol = priceHistory.get(symbol);
                    if (bySymbol == null) return Optional.empty();
                    return bySymbol.entrySet().stream()
                            .filter(e -> !e.getKey().isAfter(date))
                            .max(Map.Entry.comparingByKey())
                            .map(e -> {
                                StockPriceHistory h = new StockPriceHistory();
                                h.setSymbol(symbol);
                                h.setDate(e.getKey());
                                h.setClose(e.getValue());
                                h.setOpen(e.getValue());
                                h.setHigh(e.getValue());
                                h.setLow(e.getValue());
                                return h;
                            });
                });

        service = new PortfolioGrowthService(
                new HoldingsReconstruction(transactionRepository),
                stockPriceHistoryRepository,
                marketCalendar);
    }

    private void addTransaction(LocalDate date, String symbol, Side side, int quantity) {
        Transaction tx = new Transaction();
        tx.setStockSymbol(symbol);
        tx.setType(side);
        tx.setQuantity(quantity);
        tx.setPrice(new BigDecimal("100.00")); // doesn't affect reconstruction (uses historical price)
        tx.setTotalAmount(BigDecimal.valueOf(100L * quantity));
        tx.setTimestamp(date.atTime(LocalTime.NOON));
        allTransactions.add(tx);
    }

    private void addPriceHistory(String symbol, LocalDate date, double close) {
        priceHistory.computeIfAbsent(symbol.toUpperCase(), k -> new HashMap<>())
                .put(date, BigDecimal.valueOf(close));
    }

    // ---------- TESTS ----------

    @Test
    @DisplayName("no transactions: every day returns $0")
    void empty_returnsZeroForAllDays() {
        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_3);

        assertThat(growth).hasSize(3);
        assertThat(growth).allMatch(g -> g.value().signum() == 0);
    }

    @Test
    @DisplayName("one BUY: portfolio value uses HISTORICAL close, not current price")
    void singleBuy_valuedAtHistoricalClose() {
        addTransaction(DAY_2, "AAPL", Side.BUY, 10);
        addPriceHistory("AAPL", DAY_2, 150.0);
        addPriceHistory("AAPL", DAY_3, 160.0);
        addPriceHistory("AAPL", DAY_4, 170.0);
        addPriceHistory("AAPL", DAY_5, 200.0); // simulates "current" price drift

        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_4);

        assertThat(growth).hasSize(4);
        // DAY_1 — no position yet
        assertThat(growth.get(0).value()).isEqualByComparingTo("0");
        // DAY_2 — 10 * 150 = 1500
        assertThat(growth.get(1).value()).isEqualByComparingTo("1500");
        // DAY_3 — 10 * 160 = 1600 (uses DAY_3 price, NOT DAY_5)
        assertThat(growth.get(2).value()).isEqualByComparingTo("1600");
        // DAY_4 — 10 * 170 = 1700
        assertThat(growth.get(3).value()).isEqualByComparingTo("1700");
    }

    @Test
    @DisplayName("BUY then partial SELL: net position valued at historical price")
    void buyThenPartialSell_netPositionValued() {
        addTransaction(DAY_1, "AAPL", Side.BUY, 10);
        addTransaction(DAY_3, "AAPL", Side.SELL, 4);

        addPriceHistory("AAPL", DAY_1, 100.0);
        addPriceHistory("AAPL", DAY_2, 110.0);
        addPriceHistory("AAPL", DAY_3, 120.0);
        addPriceHistory("AAPL", DAY_4, 130.0);

        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_4);

        // DAY_1 — 10 shares × 100 = 1000
        assertThat(growth.get(0).value()).isEqualByComparingTo("1000");
        // DAY_2 — 10 shares × 110 = 1100
        assertThat(growth.get(1).value()).isEqualByComparingTo("1100");
        // DAY_3 — 6 shares × 120 = 720 (after sell)
        assertThat(growth.get(2).value()).isEqualByComparingTo("720");
        // DAY_4 — 6 shares × 130 = 780
        assertThat(growth.get(3).value()).isEqualByComparingTo("780");
    }

    @Test
    @DisplayName("BUY then full SELL: zero value from sell date onward")
    void buyThenFullSell_zeroAfterSell() {
        addTransaction(DAY_1, "AAPL", Side.BUY, 5);
        addTransaction(DAY_3, "AAPL", Side.SELL, 5);

        addPriceHistory("AAPL", DAY_1, 100.0);
        addPriceHistory("AAPL", DAY_2, 110.0);
        addPriceHistory("AAPL", DAY_3, 120.0);
        addPriceHistory("AAPL", DAY_4, 130.0);

        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_4);

        assertThat(growth.get(0).value()).isEqualByComparingTo("500");
        assertThat(growth.get(1).value()).isEqualByComparingTo("550");
        // After full sell — should be 0
        assertThat(growth.get(2).value()).isEqualByComparingTo("0");
        assertThat(growth.get(3).value()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("fallback: missing price for date uses most recent prior trading day")
    void missingPriceOnDate_fallsBackToPrior() {
        addTransaction(DAY_1, "AAPL", Side.BUY, 10);

        // DAY_2 deliberately has no entry; service should fall back to DAY_1's price
        addPriceHistory("AAPL", DAY_1, 100.0);
        addPriceHistory("AAPL", DAY_3, 130.0);

        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_3);

        assertThat(growth.get(0).value()).isEqualByComparingTo("1000");  // 10 * 100
        assertThat(growth.get(1).value()).isEqualByComparingTo("1000");  // fallback to DAY_1
        assertThat(growth.get(2).value()).isEqualByComparingTo("1300");  // 10 * 130
    }

    @Test
    @DisplayName("symbol with no historical price data: excluded from total")
    void symbolWithoutAnyHistory_excludedFromTotal() {
        addTransaction(DAY_1, "AAPL", Side.BUY, 10);
        addTransaction(DAY_1, "UNKNOWN", Side.BUY, 5);

        addPriceHistory("AAPL", DAY_1, 100.0);
        // UNKNOWN has no history at all

        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_1);

        // Only AAPL contributes: 10 * 100 = 1000. UNKNOWN excluded.
        assertThat(growth.get(0).value()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("custom range: startDate after endDate throws")
    void customRange_invalidDates_throws() {
        assertThatThrownBy(() -> service.getPortfolioGrowthCustomRange(1L, DAY_3, DAY_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date");
    }

    @Test
    @DisplayName("custom range: skips non-trading days entirely")
    void customRange_skipsNonTradingDays() {
        // Make DAY_3 a non-trading day
        when(marketCalendar.isTradingDay(eq(DAY_3))).thenReturn(false);

        List<PortfolioGrowthResponse> growth =
                service.getPortfolioGrowthCustomRange(1L, DAY_1, DAY_5);

        // 5 weekdays but DAY_3 excluded → 4 data points
        assertThat(growth).hasSize(4);
        assertThat(growth).noneMatch(g -> g.date().equals(DAY_3));
    }
}
