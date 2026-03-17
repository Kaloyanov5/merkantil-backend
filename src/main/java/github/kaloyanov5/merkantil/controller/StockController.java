package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.StockHistoryResponse;
import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.dto.response.StockResponse;
import github.kaloyanov5.merkantil.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * Get all stocks (paginated)
     * GET /api/stocks?page=0&size=20&sortBy=symbol&direction=ASC
     */
    @GetMapping
    public ResponseEntity<Page<StockResponse>> getAllStocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "symbol") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction
    ) {
        Page<StockResponse> stocks = stockService.getAllStocks(page, size, sortBy, direction);
        return ResponseEntity.ok(stocks);
    }

    /**
     * Get stock by symbol
     * GET /api/stocks/AAPL
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getStockBySymbol(@PathVariable String symbol) {
        try {
            StockResponse stock = stockService.getStockBySymbol(symbol);
            return ResponseEntity.ok(stock);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /**
     * Get real-time quote
     * GET /api/stocks/AAPL/quote
     */
    @GetMapping("/{symbol}/quote")
    public ResponseEntity<?> getQuote(@PathVariable String symbol) {
        try {
            StockQuoteResponse quote = stockService.getQuote(symbol);
            return ResponseEntity.ok(quote);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Search stocks
     * GET /api/stocks/search?q=apple&page=0&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchStocks(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            if (q == null || q.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Search query required"));
            }
            Page<StockResponse> results = stockService.searchStocks(q, page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get stocks by sector
     * GET /api/stocks/sector/Technology?page=0&size=20
     */
    @GetMapping("/sector/{sector}")
    public ResponseEntity<Page<StockResponse>> getStocksBySector(
            @PathVariable String sector,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<StockResponse> stocks = stockService.getStocksBySector(sector, page, size);
        return ResponseEntity.ok(stocks);
    }

    /**
     * Get all sectors
     * GET /api/stocks/sectors
     */
    @GetMapping("/sectors")
    public ResponseEntity<List<String>> getAllSectors() {
        List<String> sectors = stockService.getAllSectors();
        return ResponseEntity.ok(sectors);
    }

    /**
     * Get top gainers
     * GET /api/stocks/movers/gainers?limit=10
     */
    @GetMapping("/movers/gainers")
    public ResponseEntity<List<StockResponse>> getTopGainers(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<StockResponse> gainers = stockService.getTopGainers(limit);
        return ResponseEntity.ok(gainers);
    }

    /**
     * Get top losers
     * GET /api/stocks/movers/losers?limit=10
     */
    @GetMapping("/movers/losers")
    public ResponseEntity<List<StockResponse>> getTopLosers(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<StockResponse> losers = stockService.getTopLosers(limit);
        return ResponseEntity.ok(losers);
    }

    /**
     * Get most active stocks
     * GET /api/stocks/movers/active?limit=10
     */
    @GetMapping("/movers/active")
    public ResponseEntity<List<StockResponse>> getMostActive(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<StockResponse> active = stockService.getMostActive(limit);
        return ResponseEntity.ok(active);
    }

    /**
     * Get stock price history
     * GET /api/stocks/AAPL/history?startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/{symbol}/history")
    public ResponseEntity<?> getStockHistory(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        try {
            List<StockHistoryResponse> history = stockService.getStockHistory(symbol, startDate, endDate);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get multiple quotes
     * POST /api/stocks/quotes
     * Body: { "symbols": ["AAPL", "GOOGL", "MSFT"] }
     */
    @PostMapping("/quotes")
    public ResponseEntity<?> getMultipleQuotes(@RequestBody Map<String, List<String>> request) {
        try {
            List<String> symbols = request.get("symbols");
            if (symbols == null || symbols.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Symbols list is required"));
            }
            if (symbols.size() > 30) {
                return ResponseEntity.badRequest().body(Map.of("error", "Maximum 30 symbols allowed"));
            }

            List<StockQuoteResponse> quotes = stockService.getMultipleQuotes(symbols);
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}