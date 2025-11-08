package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.alpaca.AlpacaBar;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.service.StockImportService;
import github.kaloyanov5.merkantil.service.StockPriceScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import github.kaloyanov5.merkantil.service.AlpacaApiService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stocks/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class StockImportController {

    private final StockImportService stockImportService;
    private final StockPriceScheduler stockPriceScheduler;
    private final StockRepository stockRepository;
    private final AlpacaApiService alpacaApiService;

    /**
     * Import ALL tradable stocks from Alpaca
     * WARNING: This may take several minutes and use many API calls
     * POST /api/admin/stocks/import/all
     */
    @PostMapping("/all")
    public ResponseEntity<?> importAllStocks() {
        try {
            StockImportService.ImportResult result = stockImportService.importStocksFromAlpaca();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * Import top N popular stocks
     * POST /api/admin/stocks/import/top?limit=30
     */
    @PostMapping("/top")
    public ResponseEntity<?> importTopStocks(@RequestParam(defaultValue = "30") int limit) {
        try {
            if (limit <= 0 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Limit must be between 1 and 100"));
            }

            StockImportService.ImportResult result = stockImportService.importTopStocks(limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * Import a single stock by symbol
     * POST /api/admin/stocks/import/single
     * Body: { "symbol": "AAPL" }
     */
    @PostMapping("/single")
    public ResponseEntity<?> importSingleStock(@RequestBody Map<String, String> request) {
        try {
            String symbol = request.get("symbol");
            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Symbol is required"));
            }

            boolean success = stockImportService.importSingleStock(symbol);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "Stock imported successfully: " + symbol));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to import stock: " + symbol));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * Manually trigger price update (useful for testing)
     * POST /api/admin/stocks/import/update-prices
     */
    @PostMapping("/update-prices")
    public ResponseEntity<?> updatePrices() {
        try {
            stockPriceScheduler.updatePricesNow();
            return ResponseEntity.ok(Map.of("message", "Price update completed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Price update failed: " + e.getMessage()));
        }
    }

    /**
     * Backfill historical data for all stocks
     * POST /api/admin/stocks/import/backfill?startDate=2024-01-01&endDate=2024-12-31
     */
    @PostMapping("/backfill")
    public ResponseEntity<?> backfillHistory(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate endDate
    ) {
        try {
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Start date must be before end date"));
            }

            if (startDate.isBefore(java.time.LocalDate.now().minusYears(5))) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Start date cannot be more than 5 years ago"));
            }

            StockPriceScheduler.BackfillResult result = stockPriceScheduler.backfillAllStocks(startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Backfill failed: " + e.getMessage()));
        }
    }

    /**
     * Backfill historical data for single stock
     * POST /api/admin/stocks/import/backfill-single
     * Body: { "symbol": "AAPL", "startDate": "2024-01-01", "endDate": "2024-12-31" }
     */
    @PostMapping("/backfill-single")
    public ResponseEntity<?> backfillSingleStock(@RequestBody Map<String, String> request) {
        try {
            String symbol = request.get("symbol");
            String startDateStr = request.get("startDate");
            String endDateStr = request.get("endDate");

            if (symbol == null || startDateStr == null || endDateStr == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "symbol, startDate, and endDate are required"));
            }

            java.time.LocalDate startDate = java.time.LocalDate.parse(startDateStr);
            java.time.LocalDate endDate = java.time.LocalDate.parse(endDateStr);

            int records = stockPriceScheduler.backfillStockHistory(symbol, startDate, endDate);

            return ResponseEntity.ok(Map.of(
                    "message", "Backfill completed for " + symbol,
                    "recordsAdded", records
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Backfill failed: " + e.getMessage()));
        }
    }

    /**
     * Test endpoint to see raw Alpaca bars response
     * GET /api/admin/stocks/import/test-bars?symbol=AAPL&startDate=2024-10-20&endDate=2024-10-26
     */
    @GetMapping("/test-bars")
    public ResponseEntity<?> testBars(
            @RequestParam String symbol,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate endDate
    ) {
        try {
            log.info("Testing bars API for {} from {} to {}", symbol, startDate, endDate);

            // Call Alpaca API and return raw response
            List<AlpacaBar> result = alpacaApiService.getHistoricalBars(symbol, startDate, endDate);

            return ResponseEntity.ok(Map.of(
                    "success", result != null && !result.isEmpty(),
                    "barsCount", result != null ? result.size() : 0,
                    "data", result != null ? result : List.of()
            ));
        } catch (Exception e) {
            log.error("Test bars error: ", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "errorType", e.getClass().getSimpleName()
            ));
        }
    }
}