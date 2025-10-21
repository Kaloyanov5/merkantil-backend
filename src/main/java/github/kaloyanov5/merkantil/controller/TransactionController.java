package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.response.TransactionResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AuthService authService;

    /**
     * Get user's transaction history
     * GET /api/transactions?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> getUserTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User user = authService.getCurrentUser();
            Page<TransactionResponse> transactions = transactionService.getUserTransactions(
                    user.getId(), page, size);
            return ResponseEntity.ok(transactions);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get transactions by type (BUY/SELL)
     * GET /api/transactions/type/BUY?page=0&size=20
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getTransactionsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User user = authService.getCurrentUser();
            Page<TransactionResponse> transactions = transactionService.getUserTransactionsByType(
                    user.getId(), type, page, size);
            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid type: " + type));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get transactions for specific stock
     * GET /api/transactions/symbol/AAPL
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<?> getTransactionsBySymbol(@PathVariable String symbol) {
        try {
            User user = authService.getCurrentUser();
            List<TransactionResponse> transactions = transactionService.getUserTransactionsBySymbol(
                    user.getId(), symbol);
            return ResponseEntity.ok(transactions);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get transactions in date range
     * GET /api/transactions/range?start=2024-01-01T00:00:00&end=2024-12-31T23:59:59
     */
    @GetMapping("/range")
    public ResponseEntity<?> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        try {
            User user = authService.getCurrentUser();
            List<TransactionResponse> transactions = transactionService.getUserTransactionsByDateRange(
                    user.getId(), start, end);
            return ResponseEntity.ok(transactions);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }

    /**
     * Get transaction statistics
     * GET /api/transactions/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getTransactionStats() {
        try {
            User user = authService.getCurrentUser();
            TransactionService.TransactionStats stats = transactionService.getTransactionStats(user.getId());
            return ResponseEntity.ok(stats);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }
}