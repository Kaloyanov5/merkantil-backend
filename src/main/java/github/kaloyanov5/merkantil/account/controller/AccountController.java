package github.kaloyanov5.merkantil.account.controller;

import github.kaloyanov5.merkantil.account.controller.dto.request.DepositRequest;
import github.kaloyanov5.merkantil.account.controller.dto.request.TransferRequest;
import github.kaloyanov5.merkantil.account.controller.dto.response.BalanceResponse;
import github.kaloyanov5.merkantil.account.controller.dto.response.WalletTransactionResponse;
import github.kaloyanov5.merkantil.identity.model.User;
import github.kaloyanov5.merkantil.account.service.AccountService;
import github.kaloyanov5.merkantil.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Accounts", description = "Endpoints for managing accounts, balances and wallet operations")
public class AccountController {

    private final AuthService authService;
    private final AccountService accountService;

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
            BalanceResponse balance = accountService.getBalance(id);
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
            BalanceResponse balance = accountService.getBalance(currentUser.getId());
            return ResponseEntity.ok(balance);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @PostMapping("/{id}/deposit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deposit funds (admin only)",
            description = "Credits the specified user's wallet. Restricted to ADMIN until a real payment processor (PSP) is wired in; this endpoint will be reopened to end users once charges are settled through a PSP instead of credited unconditionally.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit successful, updated balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid amount or payment method"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
    public ResponseEntity<?> deposit(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        try {
            BalanceResponse balance = accountService.deposit(id, request.amount(), request.paymentMethodId());
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Withdraw funds (admin only)",
            description = "Debits the specified user's wallet. Restricted to ADMIN until a real payout integration is wired in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal successful, updated balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid amount or insufficient funds"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
    public ResponseEntity<?> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        try {
            BalanceResponse balance = accountService.withdraw(id, request.amount());
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
            BalanceResponse balance = accountService.transfer(currentUser.getId(), request);
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

    @GetMapping("/me/wallet/history")
    @Operation(summary = "Get wallet transaction history", description = "Returns a paginated list of all wallet transactions (deposits, withdrawals, transfers) for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet history returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getWalletHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        try {
            User currentUser = authService.getCurrentUser();
            Page<WalletTransactionResponse> history = accountService.getWalletHistory(currentUser.getId(), page, size);
            return ResponseEntity.ok(history);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }
}
