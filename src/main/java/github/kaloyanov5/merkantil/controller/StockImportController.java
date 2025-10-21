package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.service.StockImportService;
import github.kaloyanov5.merkantil.service.StockPriceScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/stocks/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StockImportController {

    private final StockImportService stockImportService;
    private final StockPriceScheduler stockPriceScheduler;

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
}