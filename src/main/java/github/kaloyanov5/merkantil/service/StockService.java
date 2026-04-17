package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveBar;
import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import github.kaloyanov5.merkantil.dto.response.StockHistoryResponse;
import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.dto.response.StockResponse;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;
    private final MassiveApiService massiveApiService;
    private final MarketCalendar marketCalendar;

    /**
     * Get all stocks with pagination
     */
    public Page<StockResponse> getAllStocks(int page, int size, String sortBy, String direction) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        return stockRepository.findByIsActiveTrue(pageable)
                .map(this::mapToStockResponse);
    }

    /**
     * Get stock by symbol
     * TODO: replace caching with database price access
     */
    @Cacheable(value = "stocks", key = "#symbol")
    @Transactional
    public StockResponse getStockBySymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + symbol));

        // Update price from Massive if stale (older than 5 minutes)
        if (stock.getLastUpdated() == null ||
                stock.getLastUpdated().isBefore(LocalDateTime.now().minusMinutes(5))) {
            updateStockPriceFromMassive(stock);
        }

        return mapToStockResponse(stock);
    }

    /**
     * Get real-time quote from Massive
     */
    public StockQuoteResponse getQuote(String symbol) {
        MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(symbol.toUpperCase());

        if (snapshot == null) {
            throw new IllegalArgumentException("Unable to fetch quote for: " + symbol);
        }

        String marketSession = resolveMarketSession();

        Double currentPrice = resolveRegularPrice(snapshot, marketSession);
        if (currentPrice == null) {
            throw new IllegalArgumentException("Unable to fetch quote for: " + symbol);
        }

        String name = stockRepository.findBySymbol(symbol.toUpperCase())
                .map(Stock::getName)
                .orElse(null);

        return buildQuoteResponse(symbol.toUpperCase(), name, currentPrice, snapshot, marketSession);
    }

    /**
     * Search stocks
     */
    public Page<StockResponse> searchStocks(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        return stockRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(
                        query, query, pageable)
                .map(this::mapToStockResponse);
    }

    /**
     * Get stocks by sector
     */
    public Page<StockResponse> getStocksBySector(String sector, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "marketCap"));

        return stockRepository.findBySector(sector, pageable)
                .map(this::mapToStockResponse);
    }

    /**
     * Get all sectors
     */
    public List<String> getAllSectors() {
        return stockRepository.findAllSectors();
    }

    /**
     * Get top gainers
     */
    public List<StockResponse> getTopGainers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return stockRepository.findTopGainers(pageable).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get top losers
     */
    public List<StockResponse> getTopLosers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return stockRepository.findTopLosers(pageable).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get most active (by volume)
     */
    public List<StockResponse> getMostActive(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return stockRepository.findTopByVolume(pageable).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get stock history from Massive
     */
    public List<StockHistoryResponse> getStockHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MassiveBar> bars =
                massiveApiService.getHistoricalBars(symbol.toUpperCase(), startDate, endDate);

        if (bars == null || bars.isEmpty()) {
            // fallback to database
            return stockPriceHistoryRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(symbol.toUpperCase(), startDate, endDate)
                    .stream()
                    .map(this::mapToHistoryResponse)
                    .collect(Collectors.toList());
        }

        return bars.stream()
                .map(bar -> new StockHistoryResponse(
                        MassiveApiService.millisToLocalDate(bar.getTimestamp()),
                        bar.getOpen(),
                        bar.getHigh(),
                        bar.getLow(),
                        bar.getClose(),
                        bar.getVolume()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get multiple quotes at once from Massive
     */
    public List<StockQuoteResponse> getMultipleQuotes(List<String> symbols) {
        Map<String, MassiveSnapshotTicker> snapshots = massiveApiService.getMultipleSnapshots(
                symbols.stream().map(String::toUpperCase).collect(Collectors.toList()));

        String marketSession = resolveMarketSession();

        return snapshots.entrySet().stream()
                .map(entry -> convertSnapshotToQuote(entry.getKey(), entry.getValue(), marketSession))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get detailed market status (OPEN, PRE_MARKET, AFTER_HOURS, CLOSED, HOLIDAY)
     */
    public Map<String, String> getMarketStatus() {
        Map<String, String> status = massiveApiService.getDetailedMarketStatus();
        if ("CLOSED".equals(status.get("status")) && marketCalendar.isHoliday(LocalDate.now())) {
            Map<String, String> result = new java.util.HashMap<>(status);
            result.put("status", "HOLIDAY");
            return result;
        }
        return status;
    }

    /**
     * Update stock price from Massive (used for cache-miss refresh in getStockBySymbol).
     * Delegates to the same session-aware logic used by the scheduler.
     */
    @Transactional
    protected void updateStockPriceFromMassive(Stock stock) {
        try {
            MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(stock.getSymbol());
            if (snapshot == null) return;

            String marketSession = resolveMarketSession();

            if ("OPEN".equals(marketSession)) {
                Double price = resolveRegularPrice(snapshot, marketSession);
                if (price != null) stock.setCurrentPrice(price);
                stock.setExtendedHoursPrice(null);
                if (snapshot.getDay() != null) {
                    if (snapshot.getDay().getHigh() != null && snapshot.getDay().getHigh() > 0)
                        stock.setDayHigh(snapshot.getDay().getHigh());
                    if (snapshot.getDay().getLow() != null && snapshot.getDay().getLow() > 0)
                        stock.setDayLow(snapshot.getDay().getLow());
                    if (snapshot.getDay().getVolume() != null && snapshot.getDay().getVolume() > 0)
                        stock.setVolume(snapshot.getDay().getVolume().longValue());
                }
            } else if ("PRE_MARKET".equals(marketSession) || "AFTER_HOURS".equals(marketSession)) {
                Double extPrice = resolveExtendedHoursPrice(snapshot, marketSession);
                if (extPrice != null) stock.setExtendedHoursPrice(extPrice);
                // Sync currentPrice with official regular-session close
                if (snapshot.getDay() != null && snapshot.getDay().getClose() != null
                        && snapshot.getDay().getClose() > 0) {
                    stock.setCurrentPrice(snapshot.getDay().getClose());
                } else if (snapshot.getPrevDay() != null && snapshot.getPrevDay().getClose() != null
                        && snapshot.getPrevDay().getClose() > 0) {
                    stock.setCurrentPrice(snapshot.getPrevDay().getClose());
                }
                if (snapshot.getPrevDay() != null && snapshot.getPrevDay().getClose() != null
                        && snapshot.getPrevDay().getClose() > 0) {
                    stock.setPreviousClose(snapshot.getPrevDay().getClose());
                }
            }

            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
            log.info("Updated price for {} (session: {}): regular=${}, extended=${}",
                    stock.getSymbol(), marketSession, stock.getCurrentPrice(), stock.getExtendedHoursPrice());
        } catch (Exception e) {
            log.error("Error updating price for {} from Massive: {}", stock.getSymbol(), e.getMessage());
        }
    }

    private StockQuoteResponse convertSnapshotToQuote(String symbol, MassiveSnapshotTicker snapshot,
                                                       String marketSession) {
        if (snapshot == null) return null;

        Double currentPrice = resolveRegularPrice(snapshot, marketSession);
        if (currentPrice == null) return null;

        String name = stockRepository.findBySymbol(symbol).map(Stock::getName).orElse(null);
        return buildQuoteResponse(symbol, name, currentPrice, snapshot, marketSession);
    }

    /**
     * Resolves the regular-hours price from a snapshot.
     * During OPEN: min.close / lastTrade is the live price.
     * During PRE_MARKET / AFTER_HOURS / CLOSED: prevDay.close is the official regular-session price.
     */
    private Double resolveRegularPrice(MassiveSnapshotTicker snapshot, String marketSession) {
        if ("OPEN".equals(marketSession)) {
            // Live intraday price
            if (snapshot.getLastTrade() != null && snapshot.getLastTrade().getPrice() != null
                    && snapshot.getLastTrade().getPrice() > 0) {
                return snapshot.getLastTrade().getPrice();
            }
            if (snapshot.getMin() != null && snapshot.getMin().getClose() != null
                    && snapshot.getMin().getClose() > 0) {
                return snapshot.getMin().getClose();
            }
            if (snapshot.getFmv() != null && snapshot.getFmv() > 0) {
                return snapshot.getFmv();
            }
            if (snapshot.getDay() != null && snapshot.getDay().getClose() != null
                    && snapshot.getDay().getClose() > 0) {
                return snapshot.getDay().getClose();
            }
        } else {
            // Outside regular hours — return last regular-session close
            if (snapshot.getPrevDay() != null && snapshot.getPrevDay().getClose() != null
                    && snapshot.getPrevDay().getClose() > 0) {
                return snapshot.getPrevDay().getClose();
            }
            // Fallback: day.close if prevDay unavailable
            if (snapshot.getDay() != null && snapshot.getDay().getClose() != null
                    && snapshot.getDay().getClose() > 0) {
                return snapshot.getDay().getClose();
            }
        }
        return null;
    }

    /**
     * Resolves the extended-hours price (pre-market or after-hours).
     * Only meaningful when marketSession is PRE_MARKET or AFTER_HOURS.
     */
    private Double resolveExtendedHoursPrice(MassiveSnapshotTicker snapshot, String marketSession) {
        if (!"PRE_MARKET".equals(marketSession) && !"AFTER_HOURS".equals(marketSession)) {
            return null;
        }
        if (snapshot.getLastTrade() != null && snapshot.getLastTrade().getPrice() != null
                && snapshot.getLastTrade().getPrice() > 0) {
            return snapshot.getLastTrade().getPrice();
        }
        if (snapshot.getMin() != null && snapshot.getMin().getClose() != null
                && snapshot.getMin().getClose() > 0) {
            return snapshot.getMin().getClose();
        }
        if (snapshot.getFmv() != null && snapshot.getFmv() > 0) {
            return snapshot.getFmv();
        }
        return null;
    }

    private String resolveMarketSession() {
        try {
            return marketCalendar.isHoliday(java.time.LocalDate.now())
                    ? "HOLIDAY"
                    : massiveApiService.getDetailedMarketStatus().getOrDefault("status", "CLOSED");
        } catch (Exception e) {
            log.warn("Could not determine market session, defaulting to CLOSED");
            return "CLOSED";
        }
    }

    private StockQuoteResponse buildQuoteResponse(String symbol, String name,
                                                   Double currentPrice, MassiveSnapshotTicker snapshot,
                                                   String marketSession) {
        Double previousClose = snapshot.getPrevDay() != null ? snapshot.getPrevDay().getClose() : null;

        // Prefer Massive's pre-computed change values; fall back to manual calculation
        Double change = snapshot.getTodaysChange() != null
                ? snapshot.getTodaysChange()
                : (previousClose != null ? currentPrice - previousClose : null);
        Double changePercent = snapshot.getTodaysChangePerc() != null
                ? snapshot.getTodaysChangePerc()
                : (previousClose != null && previousClose > 0 ? (currentPrice - previousClose) / previousClose * 100 : null);

        // day OHLV fields are 0 during pre/after-hours — return null instead of 0
        Double dayHigh   = snapshot.getDay() != null && snapshot.getDay().getHigh() != null
                           && snapshot.getDay().getHigh() > 0 ? snapshot.getDay().getHigh() : null;
        Double dayLow    = snapshot.getDay() != null && snapshot.getDay().getLow() != null
                           && snapshot.getDay().getLow() > 0 ? snapshot.getDay().getLow() : null;
        Double dayOpen   = snapshot.getDay() != null && snapshot.getDay().getOpen() != null
                           && snapshot.getDay().getOpen() > 0 ? snapshot.getDay().getOpen() : null;
        Long   dayVolume = snapshot.getDay() != null && snapshot.getDay().getVolume() != null
                           && snapshot.getDay().getVolume() > 0
                           ? snapshot.getDay().getVolume() : null;

        Double extendedHoursPrice = resolveExtendedHoursPrice(snapshot, marketSession);
        Double extendedHoursChange = (extendedHoursPrice != null && currentPrice != null)
                ? extendedHoursPrice - currentPrice : null;
        Double extendedHoursChangePercent = (extendedHoursChange != null && currentPrice > 0)
                ? extendedHoursChange / currentPrice * 100 : null;

        return new StockQuoteResponse(symbol, name, currentPrice, change, changePercent,
                dayHigh, dayLow, dayOpen, previousClose, dayVolume,
                extendedHoursPrice, extendedHoursChange, extendedHoursChangePercent,
                marketSession, LocalDateTime.now());
    }

    private StockResponse mapToStockResponse(Stock stock) {
        Double changeAmount = null;
        Double changePercent = null;

        if (stock.getCurrentPrice() != null && stock.getPreviousClose() != null) {
            changeAmount = stock.getCurrentPrice() - stock.getPreviousClose();
            changePercent = (changeAmount / stock.getPreviousClose()) * 100;
        }

        return new StockResponse(
                stock.getId(),
                stock.getSymbol(),
                stock.getName(),
                stock.getExchange(),
                stock.getCurrency(),
                stock.getSector(),
                stock.getIndustry(),
                stock.getCurrentPrice(),
                stock.getPreviousClose(),
                stock.getDayHigh(),
                stock.getDayLow(),
                stock.getVolume(),
                stock.getMarketCap(),
                changeAmount,
                changePercent,
                stock.getExtendedHoursPrice(),
                stock.getIsActive(),
                stock.getLastUpdated()
        );
    }

    private StockHistoryResponse mapToHistoryResponse(StockPriceHistory history) {
        return new StockHistoryResponse(
                history.getDate(),
                history.getOpen(),
                history.getHigh(),
                history.getLow(),
                history.getClose(),
                history.getVolume()
        );
    }
}
