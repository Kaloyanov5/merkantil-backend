package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.dto.response.BalanceResponse;
import github.kaloyanov5.merkantil.dto.request.DepositRequest;
import github.kaloyanov5.merkantil.dto.response.UserResponse;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            UserResponse user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        try {
            // Validate sortBy parameter to prevent injection
            if (!isValidSortField(sortBy)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid sort field: " + sortBy));
            }

            Page<UserResponse> users = userService.getAllUsers(page, size, sortBy, direction);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Search query cannot be empty"));
            }

            Page<UserResponse> users = userService.searchUsers(query.trim(), page, size);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<?> getBalance(@PathVariable Long id) {
        try {
            User currentUser = authService.getCurrentUser();
            if (!id.equals(currentUser.getId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "You can only view your own balance"));
            }
            BalanceResponse balance = userService.getBalance(id);
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @GetMapping("/me/balance")
    public ResponseEntity<?> getMyBalance() {
        try {
            User currentUser = authService.getCurrentUser();
            BalanceResponse balance = userService.getBalance(currentUser.getId());
            return ResponseEntity.ok(balance);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<?> deposit(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        try {
            User currentUser = authService.getCurrentUser();
            BalanceResponse balance = userService.deposit(id, request.getAmount(), currentUser.getId());
            return ResponseEntity.ok(Map.of(
                    "message", "Deposit successful",
                    "balance", balance
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        try {
            User currentUser = authService.getCurrentUser();
            BalanceResponse balance = userService.withdraw(id, request.getAmount(), currentUser.getId());
            return ResponseEntity.ok(Map.of(
                    "message", "Withdrawal successful",
                    "balance", balance
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    private boolean isValidSortField(String field) {
        return field.equals("id") ||
                field.equals("username") ||
                field.equals("email") ||
                field.equals("balance") ||
                field.equals("createdAt");
    }
}
