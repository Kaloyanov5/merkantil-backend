package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveBar;
import github.kaloyanov5.merkantil.dto.massive.MassiveLastTrade;
import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.dto.response.StockResponse;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockPriceHistoryRepository stockPriceHistoryRepository;
    @Mock private MassiveApiService massiveApiService;
    @Mock private MarketCalendar marketCalendar;
    @Mock private MarketSessionService marketSessionService;

    @InjectMocks
    private StockService stockService;

    // ---------- resolveExtendedHoursStatus (pure) ----------

    @Test
    void status_isNull_whenMarketOpen() {
        assertThat(StockService.resolveExtendedHoursStatus(new BigDecimal("10.00"), "OPEN")).isNull();
    }

    @Test
    void status_isNull_whenClosed() {
        assertThat(StockService.resolveExtendedHoursStatus(null, "CLOSED")).isNull();
    }

    @Test
    void status_isNoTrades_whenPreMarketAndNoPrice() {
        assertThat(StockService.resolveExtendedHoursStatus(null, "PRE_MARKET")).isEqualTo("NO_TRADES");
    }

    @Test
    void status_isTrading_whenPreMarketAndPricePresent() {
        assertThat(StockService.resolveExtendedHoursStatus(new BigDecimal("12.34"), "PRE_MARKET")).isEqualTo("TRADING");
    }

    @Test
    void status_isNoTrades_whenAfterHoursAndNoPrice() {
        assertThat(StockService.resolveExtendedHoursStatus(null, "AFTER_HOURS")).isEqualTo("NO_TRADES");
    }

    @Test
    void status_isTrading_whenAfterHoursAndPricePresent() {
        assertThat(StockService.resolveExtendedHoursStatus(new BigDecimal("12.34"), "AFTER_HOURS")).isEqualTo("TRADING");
    }

    private MassiveBar barWithClose(double close) {
        return new MassiveBar(null, null, null, close, null, null, null, null, null);
    }

    // ---------- getQuote (live snapshot) path ----------

    @Test
    void getQuote_emitsNoTrades_whenPreMarketHasNoExtendedTrade() {
        // prevDay close present (so regular price resolves), but no lastTrade/min/fmv
        MassiveSnapshotTicker snapshot = new MassiveSnapshotTicker(
                "DE", null, barWithClose(400.00), null, null, null, null, null, null, null);
        when(massiveApiService.getSnapshot("DE")).thenReturn(snapshot);
        when(marketSessionService.getCurrentSession()).thenReturn("PRE_MARKET");
        when(stockRepository.findBySymbol("DE")).thenReturn(Optional.empty());

        StockQuoteResponse quote = stockService.getQuote("DE");

        assertThat(quote.extendedHoursPrice()).isNull();
        assertThat(quote.extendedHoursStatus()).isEqualTo("NO_TRADES");
    }

    @Test
    void getQuote_emitsTrading_whenPreMarketHasExtendedTrade() {
        MassiveLastTrade lastTrade = new MassiveLastTrade(155.00, null, null, null, null, null);
        MassiveSnapshotTicker snapshot = new MassiveSnapshotTicker(
                "AAPL", null, barWithClose(150.00), null, lastTrade, null, null, null, null, null);
        when(massiveApiService.getSnapshot("AAPL")).thenReturn(snapshot);
        when(marketSessionService.getCurrentSession()).thenReturn("PRE_MARKET");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        StockQuoteResponse quote = stockService.getQuote("AAPL");

        assertThat(quote.extendedHoursPrice()).isEqualByComparingTo("155.00");
        assertThat(quote.extendedHoursStatus()).isEqualTo("TRADING");
    }

    @Test
    void getQuote_statusIsNull_whenMarketOpen() {
        MassiveLastTrade lastTrade = new MassiveLastTrade(150.00, null, null, null, null, null);
        MassiveSnapshotTicker snapshot = new MassiveSnapshotTicker(
                "AAPL", barWithClose(150.00), barWithClose(149.00), null, lastTrade, null, null, null, null, null);
        when(massiveApiService.getSnapshot("AAPL")).thenReturn(snapshot);
        when(marketSessionService.getCurrentSession()).thenReturn("OPEN");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

        StockQuoteResponse quote = stockService.getQuote("AAPL");

        assertThat(quote.extendedHoursStatus()).isNull();
    }

    // ---------- getStockBySymbol (stored entity) path ----------

    @Test
    void getStockBySymbol_emitsNoTrades_whenPreMarketAndStoredExtendedPriceNull() {
        Stock stock = new Stock();
        stock.setSymbol("DE");
        stock.setCurrentPrice(new BigDecimal("400.00"));
        stock.setPreviousClose(new BigDecimal("400.00"));
        stock.setExtendedHoursPrice(null);
        stock.setLastUpdated(LocalDateTime.now()); // fresh -> skips Massive refresh
        when(stockRepository.findBySymbol("DE")).thenReturn(Optional.of(stock));
        when(marketSessionService.getCurrentSession()).thenReturn("PRE_MARKET");

        StockResponse response = stockService.getStockBySymbol("DE");

        assertThat(response.extendedHoursPrice()).isNull();
        assertThat(response.extendedHoursStatus()).isEqualTo("NO_TRADES");
    }

    @Test
    void getStockBySymbol_statusIsNull_whenMarketClosed() {
        Stock stock = new Stock();
        stock.setSymbol("DE");
        stock.setCurrentPrice(new BigDecimal("400.00"));
        stock.setPreviousClose(new BigDecimal("400.00"));
        stock.setExtendedHoursPrice(null);
        stock.setLastUpdated(LocalDateTime.now());
        when(stockRepository.findBySymbol("DE")).thenReturn(Optional.of(stock));
        when(marketSessionService.getCurrentSession()).thenReturn("CLOSED");

        StockResponse response = stockService.getStockBySymbol("DE");

        assertThat(response.extendedHoursStatus()).isNull();
    }

    // ---------- updateStockPriceFromMassive (stale clearing) ----------

    @Test
    void updateStockPriceFromMassive_clearsStaleExtendedPrice_whenNoExtendedTrade() {
        Stock stock = new Stock();
        stock.setSymbol("DE");
        stock.setExtendedHoursPrice(new BigDecimal("399.00")); // stale from a prior session

        // PRE_MARKET snapshot with day/prevDay close but no lastTrade/min/fmv
        MassiveSnapshotTicker snapshot = new MassiveSnapshotTicker(
                "DE", barWithClose(400.00), barWithClose(398.00), null, null, null, null, null, null, null);
        when(massiveApiService.getSnapshot("DE")).thenReturn(snapshot);
        when(marketSessionService.getCurrentSession()).thenReturn("PRE_MARKET");

        stockService.updateStockPriceFromMassive(stock);

        assertThat(stock.getExtendedHoursPrice()).isNull();
    }
}
