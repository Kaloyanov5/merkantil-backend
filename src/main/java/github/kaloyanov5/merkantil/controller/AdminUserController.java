package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.*;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import github.kaloyanov5.merkantil.repository.UserRepository;
import github.kaloyanov5.merkantil.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Administrative endpoints for user management and platform statistics")
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final TransactionService transactionService;
    private final PortfolioService portfolioService;
    private final OrderService orderService;
    private final LoginSessionService loginSessionService;
    private final StockRepository stockRepository;
    private final TransactionRepository transactionRepository;

    /**
     * GET /api/admin/users/{id}/transactions
     */
    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get user transactions", description = "Returns paginated trade history for any user. Requires ADMIN role.")
    public ResponseEntity<?> getUserTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            ensureUserExists(id);
            Page<TransactionResponse> transactions = transactionService.getUserTransactions(id, page, size);
            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/users/{id}/portfolio
     */
    @GetMapping("/{id}/portfolio")
    @Operation(summary = "Get user portfolio", description = "Returns current holdings and portfolio summary for any user. Requires ADMIN role.")
    public ResponseEntity<?> getUserPortfolio(@PathVariable Long id) {
        try {
            ensureUserExists(id);
            List<PortfolioResponse> holdings = portfolioService.getUserPortfolio(id);
            PortfolioService.PortfolioSummary summary = portfolioService.getPortfolioSummary(id);
            return ResponseEntity.ok(Map.of("holdings", holdings, "summary", summary));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/users/{id}/orders
     */
    @GetMapping("/{id}/orders")
    @Operation(summary = "Get user orders", description = "Returns paginated order history for any user. Requires ADMIN role.")
    public ResponseEntity<?> getUserOrders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            ensureUserExists(id);
            Page<OrderResponse> orders = orderService.getUserOrders(id, page, size);
            return ResponseEntity.ok(orders);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/users/{id}/wallet
     */
    @GetMapping("/{id}/wallet")
    @Operation(summary = "Get user wallet", description = "Returns balance and wallet transaction history for any user. Requires ADMIN role.")
    public ResponseEntity<?> getUserWallet(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            ensureUserExists(id);
            BalanceResponse balance = userService.getBalance(id);
            Page<WalletTransactionResponse> history = userService.getWalletHistory(id, page, size);
            return ResponseEntity.ok(Map.of("balance", balance, "history", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/users/{id}/sessions
     */
    @GetMapping("/{id}/sessions")
    @Operation(summary = "Get user active sessions", description = "Returns active login sessions for any user. Requires ADMIN role.")
    public ResponseEntity<?> getUserSessions(@PathVariable Long id) {
        try {
            ensureUserExists(id);
            List<LoginSessionResponse> sessions = loginSessionService.getActiveSessions(id, null);
            return ResponseEntity.ok(sessions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/users/{id}/ban
     */
    @PostMapping("/{id}/ban")
    @Operation(summary = "Ban user", description = "Bans a user account and revokes all active sessions. Requires ADMIN role.")
    public ResponseEntity<?> banUser(@PathVariable Long id) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (Boolean.TRUE.equals(user.getBanned())) {
                return ResponseEntity.badRequest().body(Map.of("error", "User is already banned"));
            }
            user.setBanned(true);
            userRepository.save(user);
            loginSessionService.revokeAllSessions(id);
            return ResponseEntity.ok(Map.of("message", "User banned and all sessions revoked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/users/{id}/unban
     */
    @PostMapping("/{id}/unban")
    @Operation(summary = "Unban user", description = "Restores a banned user account. Requires ADMIN role.")
    public ResponseEntity<?> unbanUser(@PathVariable Long id) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (!Boolean.TRUE.equals(user.getBanned())) {
                return ResponseEntity.badRequest().body(Map.of("error", "User is not banned"));
            }
            user.setBanned(false);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    @Operation(summary = "Platform statistics", description = "Returns platform-wide statistics. Requires ADMIN role.")
    public ResponseEntity<?> getStats() {
        long totalUsers = userRepository.count();
        long newUsersThisWeek = userRepository.countUsersCreatedAfter(LocalDateTime.now().minusDays(7));
        long totalTransactions = transactionRepository.count();
        Double totalVolume = transactionRepository.sumTotalVolume();
        long activeStocks = stockRepository.countByIsActiveTrue();

        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "newUsersThisWeek", newUsersThisWeek,
                "totalTransactions", totalTransactions,
                "totalVolume", totalVolume != null ? totalVolume : 0.0,
                "activeStocks", activeStocks
        ));
    }

    private void ensureUserExists(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found");
        }
    }
}
