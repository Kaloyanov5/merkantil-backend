package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.massive.MassiveBar;
import github.kaloyanov5.merkantil.dto.request.BackfillSingleRequest;
import github.kaloyanov5.merkantil.dto.request.ImportMultipleRequest;
import github.kaloyanov5.merkantil.dto.request.ImportSingleRequest;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.service.MassiveApiService;
import github.kaloyanov5.merkantil.service.StockImportService;
import github.kaloyanov5.merkantil.service.StockPriceScheduler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stocks/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
@Slf4j
public class StockImportController {

    private final StockImportService stockImportService;
    private final StockPriceScheduler stockPriceScheduler;
    private final StockRepository stockRepository;
    private final MassiveApiService massiveApiService;

    /**
     * Import ALL tradable stocks from Massive
     * WARNING: This may take several minutes and use many API calls
     * POST /api/admin/stocks/import/all
     */
    @PostMapping("/all")
    public ResponseEntity<StockImportService.ImportResult> importAllStocks() {
        StockImportService.ImportResult result = stockImportService.importStocksFromMassive();
        return ResponseEntity.ok(result);
    }

    /**
     * Import top N popular stocks
     * POST /api/admin/stocks/import/top?limit=30
     */
    @PostMapping("/top")
    public ResponseEntity<?> importTopStocks(@RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        StockImportService.ImportResult result = stockImportService.importTopStocks(limit);
        return ResponseEntity.ok(result);
    }

    /**
     * Import a single stock by symbol
     * POST /api/admin/stocks/import/single
     * Body: { "symbol": "AAPL" }
     */
    @PostMapping("/single")
    public ResponseEntity<?> importSingleStock(@Valid @RequestBody ImportSingleRequest request) {
        String symbol = request.symbol();
        StockImportService.SingleImportResult result = stockImportService.importSingleStock(symbol);
        return switch (result) {
            case CREATED -> ResponseEntity.ok(Map.of("message", "Stock imported: " + symbol, "status", "created"));
            case UPDATED -> ResponseEntity.ok(Map.of("message", "Stock already exists, data refreshed: " + symbol, "status", "updated"));
            case FAILED -> ResponseEntity.badRequest().body(Map.of("error", "Stock not found or inactive: " + symbol));
        };
    }

    /**
     * Import multiple specific stocks by list of symbols
     * POST /api/admin/stocks/import/multiple
     * Body: { "symbols": ["AAPL", "MSFT", "GOOGL", "AMZN"] }
     */
    @PostMapping("/multiple")
    public ResponseEntity<?> importMultipleStocks(@Valid @RequestBody ImportMultipleRequest request) {
        List<String> symbols = request.symbols();

        int imported = 0;
        int failed = 0;
        List<String> failedSymbols = new ArrayList<>();

        // Per-symbol catch is intentional: one bad symbol should not abort the batch.
        for (String symbol : symbols) {
            try {
                StockImportService.SingleImportResult result =
                        stockImportService.importSingleStock(symbol.toUpperCase());
                if (result == StockImportService.SingleImportResult.FAILED) {
                    failed++;
                    failedSymbols.add(symbol);
                } else {
                    imported++;
                }
            } catch (Exception e) {
                failed++;
                failedSymbols.add(symbol);
                log.warn("Import failed for symbol {}: {}", symbol, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Import completed",
                "total", symbols.size(),
                "imported", imported,
                "failed", failed,
                "failedSymbols", failedSymbols
        ));
    }

    /**
     * Manually trigger price update (useful for testing)
     * POST /api/admin/stocks/import/update-prices
     */
    @PostMapping("/update-prices")
    public ResponseEntity<?> updatePrices() {
        stockPriceScheduler.updatePricesNow();
        return ResponseEntity.ok(Map.of("message", "Price update completed successfully"));
    }

    /**
     * Backfill historical data for all stocks
     * POST /api/admin/stocks/import/backfill?startDate=2024-01-01&endDate=2024-12-31
     */
    @PostMapping("/backfill")
    public ResponseEntity<?> backfillHistory(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Start date must be before end date"));
        }
        if (startDate.isBefore(LocalDate.now().minusYears(5))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Start date cannot be more than 5 years ago"));
        }

        StockPriceScheduler.BackfillResult result = stockPriceScheduler.backfillAllStocks(startDate, endDate);
        return ResponseEntity.ok(result);
    }

    /**
     * Backfill historical data for single stock
     * POST /api/admin/stocks/import/backfill-single
     * Body: { "symbol": "AAPL", "startDate": "2024-01-01", "endDate": "2024-12-31" }
     */
    @PostMapping("/backfill-single")
    public ResponseEntity<?> backfillSingleStock(@Valid @RequestBody BackfillSingleRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Start date must be before or equal to end date"));
        }

        int records = stockPriceScheduler.backfillStockHistory(
                request.symbol(), request.startDate(), request.endDate());

        return ResponseEntity.ok(Map.of(
                "message", "Backfill completed for " + request.symbol(),
                "recordsAdded", records
        ));
    }

    /**
     * Test endpoint to see raw Massive bars response
     * GET /api/admin/stocks/import/test-bars?symbol=AAPL&startDate=2024-10-20&endDate=2024-10-26
     */
    @GetMapping("/test-bars")
    public ResponseEntity<?> testBars(
            @RequestParam
            @Size(max = 10)
            @Pattern(regexp = "^[A-Za-z0-9.\\-]+$", message = "symbol may only contain letters, digits, dot and hyphen")
            String symbol,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
        log.info("Testing bars API for {} from {} to {}",
                github.kaloyanov5.merkantil.util.LogSanitizer.safe(symbol), startDate, endDate);

        List<MassiveBar> result = massiveApiService.getHistoricalBars(symbol, startDate, endDate);

        return ResponseEntity.ok(Map.of(
                "success", result != null && !result.isEmpty(),
                "barsCount", result != null ? result.size() : 0,
                "data", result != null ? result : List.of()
        ));
    }
}
