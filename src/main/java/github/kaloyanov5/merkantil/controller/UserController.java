package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.dto.response.BalanceResponse;
import github.kaloyanov5.merkantil.dto.request.DepositRequest;
import github.kaloyanov5.merkantil.dto.response.UserResponse;
import github.kaloyanov5.merkantil.dto.response.LoginSessionResponse;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.LoginSessionService;
import github.kaloyanov5.merkantil.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import github.kaloyanov5.merkantil.dto.request.ChangePasswordRequest;
import github.kaloyanov5.merkantil.dto.request.TransferRequest;
import github.kaloyanov5.merkantil.dto.response.WalletTransactionResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Endpoints for managing user accounts, balances, wallet operations, sessions and password changes")
public class UserController {

    private final UserService userService;
    private final LoginSessionService loginSessionService;
    private final AuthService authService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID", description = "Returns the profile of any user by their ID. Requires ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found and returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID or user not found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
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
    @Operation(summary = "Get all users (paginated)", description = "Returns a paginated list of all registered users. Requires ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameters"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
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
    @Operation(summary = "Search users", description = "Searches users by name or email query string. Requires ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results returned successfully"),
            @ApiResponse(responseCode = "400", description = "Empty or invalid search query"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
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

    @GetMapping("/lookup")
    @Operation(summary = "Look up user by email", description = "Returns basic user info (e.g. name) for the given email address, used for recipient lookup during transfers")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No user found with the given email")
    })
    public ResponseEntity<?> lookupByEmail(@RequestParam String email) {
        Map<String, String> result = userService.lookupByEmail(email);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get balance by user ID", description = "Returns the wallet balance for the specified user. Users may only query their own balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID or user not found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Cannot view another user's balance")
    })
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
    @Operation(summary = "Get my balance", description = "Returns the wallet balance of the currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
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
    @Operation(summary = "Deposit funds", description = "Deposits the specified amount into the user's wallet. The authenticated user must match the path ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit successful, updated balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid amount or payment method"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> deposit(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        try {
            User currentUser = authService.getCurrentUser();
            BalanceResponse balance = userService.deposit(id, request.getAmount(), currentUser.getId(), request.getPaymentMethodId());
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
    @Operation(summary = "Withdraw funds", description = "Withdraws the specified amount from the user's wallet. The authenticated user must match the path ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal successful, updated balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid amount or insufficient funds"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        try {
            User currentUser = authService.getCurrentUser();
            BalanceResponse balance = userService.withdraw(id, request.getAmount(), currentUser.getId()); // paymentMethodId not used for withdrawals
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

    @PostMapping("/me/transfer")
    @Operation(summary = "Transfer funds", description = "Transfers funds from the authenticated user's wallet to another user identified by email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer successful, updated balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid amount, recipient not found or insufficient funds"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest request) {
        try {
            User currentUser = authService.getCurrentUser();
            BalanceResponse balance = userService.transfer(currentUser.getId(), request);
            return ResponseEntity.ok(Map.of(
                    "message", "Transfer successful",
                    "balance", balance
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Change password", description = "Changes the authenticated user's password. All sessions are invalidated after a successful change and the user must log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully, user is logged out"),
            @ApiResponse(responseCode = "400", description = "Current password incorrect or new password fails validation"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        try {
            User currentUser = authService.getCurrentUser();
            userService.changePassword(currentUser.getId(), request);
            authService.logout(httpRequest, httpResponse);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully. Please log in again."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @GetMapping("/me/wallet/history")
    @Operation(summary = "Get wallet transaction history", description = "Returns a paginated list of all wallet transactions (deposits, withdrawals, transfers) for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet history returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getWalletHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User currentUser = authService.getCurrentUser();
            Page<WalletTransactionResponse> history = userService.getWalletHistory(currentUser.getId(), page, size);
            return ResponseEntity.ok(history);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @GetMapping("/me/sessions")
    @Operation(summary = "Get active sessions", description = "Returns all currently active login sessions for the authenticated user. The current session is indicated in the response.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active sessions returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getActiveSessions(HttpServletRequest httpRequest) {
        try {
            User currentUser = authService.getCurrentUser();
            String currentSessionId = httpRequest.getSession(false) != null
                    ? httpRequest.getSession(false).getId() : null;
            java.util.List<LoginSessionResponse> sessions =
                    loginSessionService.getActiveSessions(currentUser.getId(), currentSessionId);
            return ResponseEntity.ok(sessions);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @Operation(summary = "Revoke a session", description = "Revokes (terminates) a specific active login session belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked successfully"),
            @ApiResponse(responseCode = "400", description = "Session not found or does not belong to the current user"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> revokeSession(@PathVariable String sessionId) {
        try {
            User currentUser = authService.getCurrentUser();
            loginSessionService.revokeSession(currentUser.getId(), sessionId);
            return ResponseEntity.ok(Map.of("message", "Session revoked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    private boolean isValidSortField(String field) {
        return field.equals("id") ||
                field.equals("firstName") ||
                field.equals("lastName") ||
                field.equals("email") ||
                field.equals("balance") ||
                field.equals("createdAt");
    }
}
