package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.request.PaymentMethodRequest;
import github.kaloyanov5.merkantil.dto.response.PaymentMethodResponse;
import github.kaloyanov5.merkantil.entity.PaymentMethod;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.repository.PaymentMethodRepository;
import github.kaloyanov5.merkantil.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;

    public PaymentMethodResponse addPaymentMethod(Long userId, PaymentMethodRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        PaymentMethod pm = new PaymentMethod();
        pm.setUser(user);
        pm.setCardholderName(request.getCardholderName());
        pm.setLast4(request.getLast4());
        pm.setExpiryMonth(request.getExpiryMonth());
        pm.setExpiryYear(request.getExpiryYear());
        pm.setCardType(request.getCardType().toUpperCase());

        PaymentMethod saved = paymentMethodRepository.save(pm);
        return mapToResponse(saved);
    }

    public List<PaymentMethodResponse> getPaymentMethods(Long userId) {
        return paymentMethodRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void deletePaymentMethod(Long userId, Long paymentMethodId) {
        PaymentMethod pm = paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(paymentMethodId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));
        pm.setDeletedAt(LocalDateTime.now());
        paymentMethodRepository.save(pm);
    }

    private PaymentMethodResponse mapToResponse(PaymentMethod pm) {
        return new PaymentMethodResponse(
                pm.getId(),
                pm.getCardholderName(),
                pm.getLast4(),
                pm.getExpiryMonth(),
                pm.getExpiryYear(),
                pm.getCardType(),
                pm.getCreatedAt()
        );
    }
}
