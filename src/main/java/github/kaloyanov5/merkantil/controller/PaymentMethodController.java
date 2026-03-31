package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.request.PaymentMethodRequest;
import github.kaloyanov5.merkantil.dto.response.PaymentMethodResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<?> getPaymentMethods() {
        try {
            User currentUser = authService.getCurrentUser();
            List<PaymentMethodResponse> methods = paymentMethodService.getPaymentMethods(currentUser.getId());
            return ResponseEntity.ok(methods);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @PostMapping
    public ResponseEntity<?> addPaymentMethod(@Valid @RequestBody PaymentMethodRequest request) {
        try {
            User currentUser = authService.getCurrentUser();
            PaymentMethodResponse response = paymentMethodService.addPaymentMethod(currentUser.getId(), request);
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePaymentMethod(@PathVariable Long id) {
        try {
            User currentUser = authService.getCurrentUser();
            paymentMethodService.deletePaymentMethod(currentUser.getId(), id);
            return ResponseEntity.ok(Map.of("message", "Payment method removed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
    }
}
