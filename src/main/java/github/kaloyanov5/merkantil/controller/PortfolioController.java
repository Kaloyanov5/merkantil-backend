package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.PortfolioResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final AuthService authService;

    /**
     * Get user's portfolio
     * GET /api/portfolio
     */
    @GetMapping
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
}