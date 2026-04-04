package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.PortfolioGrowthResponse;
import github.kaloyanov5.merkantil.dto.response.PortfolioResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.PortfolioGrowthService;
import github.kaloyanov5.merkantil.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Endpoints for viewing the authenticated user's stock portfolio, positions and historical growth")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioGrowthService portfolioGrowthService;
    private final AuthService authService;

    /**
     * Get user's portfolio
     * GET /api/portfolio
     */
    @GetMapping
    @Operation(summary = "Get portfolio", description = "Returns all current stock positions held by the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portfolio returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getUserPortfolio() {
        try {
            User user = authService.getCurrentUser();
            List<PortfolioResponse> portfolio = portfolioService.getUserPortfolio(user.getId());
            return ResponseEntity.ok(portfolio);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get portfolio summary
     * GET /api/portfolio/summary
     */
    @GetMapping("/summary")
    @Operation(summary = "Get portfolio summary", description = "Returns aggregated portfolio metrics including total value, total cost basis and overall gain/loss for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portfolio summary returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getPortfolioSummary() {
        try {
            User user = authService.getCurrentUser();
            PortfolioService.PortfolioSummary summary = portfolioService.getPortfolioSummary(user.getId());
            return ResponseEntity.ok(summary);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get position for specific stock
     * GET /api/portfolio/AAPL
     */
    @GetMapping("/{symbol}")
    @Operation(summary = "Get position by symbol", description = "Returns the authenticated user's current position (shares held, cost basis, current value) for the specified stock symbol")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Position returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No position found for the given symbol")
    })
    public ResponseEntity<?> getPosition(@PathVariable String symbol) {
        try {
            User user = authService.getCurrentUser();
            PortfolioResponse position = portfolioService.getPosition(user.getId(), symbol);
            return ResponseEntity.ok(position);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get 30-day portfolio growth chart
     * GET /api/portfolio/growth
     *
     * Returns exactly one data point per trading day for the last 30 trading days.
     * Values are reconstructed using historical prices and positions as of each day.
     * This ensures financial accuracy - past values never use current prices.
     */
    @GetMapping("/growth")
    @Operation(summary = "Get 30-day portfolio growth", description = "Returns one data point per trading day for the last 30 trading days. Values are reconstructed using historical prices and positions as of each day to ensure financial accuracy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Growth data returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Failed to calculate portfolio growth")
    })
    public ResponseEntity<?> getPortfolioGrowth() {
        try {
            User user = authService.getCurrentUser();
            List<PortfolioGrowthResponse> growth = portfolioGrowthService
                    .getPortfolioGrowth30Days(user.getId());
            return ResponseEntity.ok(growth);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate portfolio growth: " + e.getMessage()));
        }
    }

    /**
     * Get portfolio growth for custom date range
     * GET /api/portfolio/growth/range?startDate=2025-01-01&endDate=2025-12-31
     *
     * Returns portfolio values for each trading day in the specified range.
     * Same financial reconstruction principles apply.
     */
    @GetMapping("/growth/range")
    @Operation(summary = "Get portfolio growth for a custom date range", description = "Returns portfolio values for each trading day in the specified range. Uses the same historical price reconstruction as the 30-day endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Growth data returned successfully"),
            @ApiResponse(responseCode = "400", description = "Start date is after end date"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Failed to calculate portfolio growth")
    })
    public ResponseEntity<?> getPortfolioGrowthCustomRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        try {
            User user = authService.getCurrentUser();

            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Start date must be before or equal to end date"));
            }

            List<PortfolioGrowthResponse> growth = portfolioGrowthService
                    .getPortfolioGrowthCustomRange(user.getId(), startDate, endDate);
            return ResponseEntity.ok(growth);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate portfolio growth: " + e.getMessage()));
        }
    }
}
