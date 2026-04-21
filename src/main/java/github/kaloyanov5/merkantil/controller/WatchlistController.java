package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist", description = "Endpoints for managing the authenticated user's stock watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Get watchlist", description = "Returns all stocks in the authenticated user's watchlist with current price quotes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Watchlist returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getWatchlist() {
        try {
            User user = authService.getCurrentUser();
            List<StockQuoteResponse> watchlist = watchlistService.getWatchlist(user);
            return ResponseEntity.ok(watchlist);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    @PostMapping("/{symbol}")
    @Operation(summary = "Add stock to watchlist", description = "Adds the specified stock symbol to the authenticated user's watchlist")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock added to watchlist"),
            @ApiResponse(responseCode = "400", description = "Stock not found or already in watchlist"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> addToWatchlist(@PathVariable String symbol) {
        try {
            User user = authService.getCurrentUser();
            watchlistService.addToWatchlist(user, symbol);
            return ResponseEntity.ok(Map.of("message", symbol.toUpperCase() + " added to watchlist"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    @DeleteMapping("/{symbol}")
    @Operation(summary = "Remove stock from watchlist", description = "Removes the specified stock symbol from the authenticated user's watchlist")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock removed from watchlist"),
            @ApiResponse(responseCode = "400", description = "Stock not in watchlist"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> removeFromWatchlist(@PathVariable String symbol) {
        try {
            User user = authService.getCurrentUser();
            watchlistService.removeFromWatchlist(user, symbol);
            return ResponseEntity.ok(Map.of("message", symbol.toUpperCase() + " removed from watchlist"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }
}
