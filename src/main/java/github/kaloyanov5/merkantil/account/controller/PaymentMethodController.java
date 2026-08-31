package github.kaloyanov5.merkantil.account.controller;

import github.kaloyanov5.merkantil.account.controller.dto.request.PaymentMethodRequest;
import github.kaloyanov5.merkantil.account.controller.dto.response.PaymentMethodResponse;
import github.kaloyanov5.merkantil.identity.model.User;
import github.kaloyanov5.merkantil.identity.service.AuthService;
import github.kaloyanov5.merkantil.account.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Payment Methods", description = "Endpoints for managing saved payment methods for the authenticated user")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Get saved payment methods", description = "Returns all active (non-deleted) payment methods saved by the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment methods returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> getPaymentMethods() {
        User currentUser = authService.getCurrentUser();
        List<PaymentMethodResponse> methods = paymentMethodService.getPaymentMethods(currentUser.getId());
        return ResponseEntity.ok(methods);
    }

    @PostMapping
    @Operation(summary = "Add a payment method", description = "Saves a new payment method for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment method added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment method details or duplicate entry"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> addPaymentMethod(@Valid @RequestBody PaymentMethodRequest request) {
        User currentUser = authService.getCurrentUser();
        PaymentMethodResponse response = paymentMethodService.addPaymentMethod(currentUser.getId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a payment method", description = "Soft-deletes (deactivates) the specified payment method belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment method removed successfully"),
            @ApiResponse(responseCode = "400", description = "Payment method not found or does not belong to the current user"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> deletePaymentMethod(@PathVariable Long id) {
        User currentUser = authService.getCurrentUser();
        paymentMethodService.deletePaymentMethod(currentUser.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Payment method removed"));
    }
}
