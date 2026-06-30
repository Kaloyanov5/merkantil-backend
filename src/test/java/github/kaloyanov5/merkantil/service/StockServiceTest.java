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
}
