package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.alpaca.AlpacaBar;
import github.kaloyanov5.merkantil.dto.alpaca.AlpacaSnapshot;
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
    private final AlpacaApiService alpacaApiService;

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

        // Update price from Alpaca if stale (older than 5 minutes)
        if (stock.getLastUpdated() == null ||
                stock.getLastUpdated().isBefore(LocalDateTime.now().minusMinutes(5))) {
            updateStockPriceFromAlpaca(stock);
        }

        return mapToStockResponse(stock);
    }

    /**
     * Get real-time quote from Alpaca
     */
    public StockQuoteResponse getQuote(String symbol) {
        AlpacaSnapshot snapshot = alpacaApiService.getSnapshot(symbol.toUpperCase());

        if (snapshot == null || snapshot.getLatestTrade() == null) {
            throw new IllegalArgumentException("Unable to fetch quote for: " + symbol);
        }

        Double currentPrice = snapshot.getLatestTrade().getPrice();
        Double previousClose = snapshot.getPrevDailyBar() != null
                ? snapshot.getPrevDailyBar().getClose()
                : null;

        Double change = null;
        Double changePercent = null;

        if (previousClose != null && currentPrice != null) {
            change = currentPrice - previousClose;
            changePercent = (change / previousClose) * 100;
        }

        // Get stock name from database
        String name = stockRepository.findBySymbol(symbol.toUpperCase())
                .map(Stock::getName)
                .orElse(null);

        return new StockQuoteResponse(
                symbol.toUpperCase(),
                name,
                currentPrice,
                change,
                changePercent,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getHigh() : null,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getLow() : null,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getOpen() : null,
                previousClose,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getVolume() : null,
                LocalDateTime.now()
        );
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
     * Get stock history from Alpaca
     */
    public List<StockHistoryResponse> getStockHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        // fetch from Alpaca (now returns List<AlpacaBar> directly)
        List<AlpacaBar> bars =
                alpacaApiService.getHistoricalBars(symbol.toUpperCase(), startDate, endDate);

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
                        LocalDate.parse(bar.getTimestamp().substring(0, 10)),
                        bar.getOpen(),
                        bar.getHigh(),
                        bar.getLow(),
                        bar.getClose(),
                        bar.getVolume()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get multiple quotes at once from Alpaca
     */
    public List<StockQuoteResponse> getMultipleQuotes(List<String> symbols) {
        Map<String, AlpacaSnapshot> snapshots = alpacaApiService.getMultipleSnapshots(
                symbols.stream().map(String::toUpperCase).collect(Collectors.toList()));

        return snapshots.entrySet().stream()
                .map(entry -> convertSnapshotToQuote(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Check if market is open
     */
    public boolean isMarketOpen() {
        return alpacaApiService.isMarketOpen();
    }

    /**
     * Update stock price from Alpaca
     */
    @Transactional
    protected void updateStockPriceFromAlpaca(Stock stock) {
        try {
            AlpacaSnapshot snapshot = alpacaApiService.getSnapshot(stock.getSymbol());

            if (snapshot != null && snapshot.getLatestTrade() != null) {
                stock.setCurrentPrice(snapshot.getLatestTrade().getPrice());

                if (snapshot.getPrevDailyBar() != null) {
                    stock.setPreviousClose(snapshot.getPrevDailyBar().getClose());
                }

                if (snapshot.getDailyBar() != null) {
                    stock.setDayHigh(snapshot.getDailyBar().getHigh());
                    stock.setDayLow(snapshot.getDailyBar().getLow());
                    stock.setVolume(snapshot.getDailyBar().getVolume());
                }

                stock.setLastUpdated(LocalDateTime.now());
                stockRepository.save(stock);

                log.info("Updated price for {} from Alpaca: ${}", stock.getSymbol(), stock.getCurrentPrice());
            }
        } catch (Exception e) {
            log.error("Error updating price for {} from Alpaca: {}", stock.getSymbol(), e.getMessage());
        }
    }

    private StockQuoteResponse convertSnapshotToQuote(String symbol, AlpacaSnapshot snapshot) {
        if (snapshot == null || snapshot.getLatestTrade() == null) {
            return null;
        }

        Double currentPrice = snapshot.getLatestTrade().getPrice();
        Double previousClose = snapshot.getPrevDailyBar() != null
                ? snapshot.getPrevDailyBar().getClose()
                : null;

        Double change = null;
        Double changePercent = null;

        if (previousClose != null && currentPrice != null) {
            change = currentPrice - previousClose;
            changePercent = (change / previousClose) * 100;
        }

        String name = stockRepository.findBySymbol(symbol).map(Stock::getName).orElse(null);

        return new StockQuoteResponse(
                symbol,
                name,
                currentPrice,
                change,
                changePercent,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getHigh() : null,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getLow() : null,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getOpen() : null,
                previousClose,
                snapshot.getDailyBar() != null ? snapshot.getDailyBar().getVolume() : null,
                LocalDateTime.now()
        );
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