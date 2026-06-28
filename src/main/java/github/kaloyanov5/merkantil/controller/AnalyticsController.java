package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.configuration.AnalyticsProperties;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AnalyticsWindow;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.PortfolioAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/portfolio/analytics")
@RequiredArgsConstructor
@Tag(name = "Portfolio Analytics",
        description = "Proprietary risk, performance and diversification metrics for the authenticated user's portfolio")
public class AnalyticsController {

    private final PortfolioAnalyticsService analyticsService;
    private final AuthService authService;
    private final AnalyticsProperties analyticsProperties;

    @GetMapping
    @Operation(summary = "Get portfolio analytics",
            description = "Risk (volatility, Sharpe, Sortino, max drawdown), benchmark (beta, alpha, R²), "
                    + "returns (time-weighted + money-weighted XIRR) and diversification over a window "
                    + "(1M, 3M, 6M, 1Y, YTD, ALL).")
    public ResponseEntity<?> getAnalytics(@RequestParam(required = false) String window) {
        AnalyticsWindow resolved;
        try {
            resolved = AnalyticsWindow.fromCode(window != null ? window : analyticsProperties.defaultWindow());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid window: " + window + ". Allowed: 1M, 3M, 6M, 1Y, YTD, ALL"));
        }
        User user = authService.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getAnalytics(user.getId(), resolved));
    }
}
