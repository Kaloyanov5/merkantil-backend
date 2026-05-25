package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.util.MarketCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Single source of truth for the current US market session.
 *
 * <p>Consolidates the session-resolution logic that was previously duplicated
 * in {@code StockService} and {@code StockPriceScheduler}. The result is cached
 * (the 1-minute default TTL configured in {@code CacheConfig}) so that order
 * placement, quote lookups and the 30-second price scheduler do not each hit
 * the Massive market-status endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSessionService {

    private final MassiveApiService massiveApiService;
    private final MarketCalendar marketCalendar;

    /**
     * The current session: {@code OPEN}, {@code PRE_MARKET}, {@code AFTER_HOURS},
     * {@code CLOSED} or {@code HOLIDAY}. Falls back to {@code CLOSED} when the
     * market status cannot be determined, so callers fail safe.
     */
    @Cacheable("marketStatus")
    public String getCurrentSession() {
        try {
            if (marketCalendar.isHoliday(LocalDate.now())) {
                return "HOLIDAY";
            }
            return massiveApiService.getDetailedMarketStatus().getOrDefault("status", "CLOSED");
        } catch (Exception e) {
            log.warn("Could not determine market session, defaulting to CLOSED: {}", e.getMessage());
            return "CLOSED";
        }
    }
}
