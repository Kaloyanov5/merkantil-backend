package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.StockHistoryResponse;
import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.dto.response.StockResponse;
import github.kaloyanov5.merkantil.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Stocks", description = "Endpoints for browsing stocks, retrieving quotes, price history, market movers and market status")
public class StockController {

    private final StockService stockService;

    /**
     * Get all stocks (paginated)
     * GET /api/stocks?page=0&size=20&sortBy=symbol&direction=ASC
     */
    @GetMapping
    @Operation(summary = "Get all stocks", description = "Returns a paginated, sortable list of all stocks available on the platform")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stocks returned successfully")
    })
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
     * Get market status
     * GET /api/stocks/market-status
     */
    @GetMapping("/market-status")
    @Operation(summary = "Get market status", description = "Returns the current status of the stock market (open/closed) along with the next open/close time")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Market status returned successfully")
    })
    public ResponseEntity<Map<String, String>> getMarketStatus() {
        Map<String, String> status = stockService.getMarketStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get stock by symbol
     * GET /api/stocks/AAPL
     */
    @GetMapping("/{symbol}")
    @Operation(summary = "Get stock by symbol", description = "Returns detailed information about a single stock identified by its ticker symbol")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock found and returned successfully"),
            @ApiResponse(responseCode = "400", description = "Stock not found for the given symbol"),
            @ApiResponse(responseCode = "500", description = "Internal error while retrieving stock data")
    })
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
    @Operation(summary = "Get real-time quote", description = "Returns the latest real-time price quote for the specified stock symbol")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote returned successfully"),
            @ApiResponse(responseCode = "400", description = "Stock not found for the given symbol")
    })
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
    @Operation(summary = "Search stocks", description = "Searches stocks by symbol or company name matching the given query string")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results returned successfully"),
            @ApiResponse(responseCode = "400", description = "Empty or invalid search query")
    })
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
    @Operation(summary = "Get stocks by sector", description = "Returns a paginated list of stocks belonging to the specified market sector")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stocks for the sector returned successfully")
    })
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
    @Operation(summary = "Get all sectors", description = "Returns a list of all distinct market sectors that have at least one stock on the platform")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sectors returned successfully")
    })
    public ResponseEntity<List<String>> getAllSectors() {
        List<String> sectors = stockService.getAllSectors();
        return ResponseEntity.ok(sectors);
    }

    /**
     * Get top gainers
     * GET /api/stocks/movers/gainers?limit=10
     */
    @GetMapping("/movers/gainers")
    @Operation(summary = "Get top gainers", description = "Returns the stocks with the highest percentage price increase for the current trading day")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Top gainers returned successfully")
    })
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
    @Operation(summary = "Get top losers", description = "Returns the stocks with the highest percentage price decrease for the current trading day")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Top losers returned successfully")
    })
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
    @Operation(summary = "Get most active stocks", description = "Returns the stocks with the highest trading volume for the current trading day")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Most active stocks returned successfully")
    })
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
    @Operation(summary = "Get stock price history", description = "Returns daily OHLCV price history for the specified stock symbol within the given date range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Price history returned successfully (empty list if no data found)")
    })
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
    @Operation(summary = "Get multiple quotes", description = "Returns real-time price quotes for a batch of stock symbols provided in the request body. Maximum 30 symbols per request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quotes returned successfully"),
            @ApiResponse(responseCode = "400", description = "Symbols list is missing, empty or exceeds the 30-symbol limit")
    })
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
