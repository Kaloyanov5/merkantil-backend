package github.kaloyanov5.merkantil.account.service;

import github.kaloyanov5.merkantil.account.controller.dto.request.PaymentMethodRequest;
import github.kaloyanov5.merkantil.account.controller.dto.response.PaymentMethodResponse;
import github.kaloyanov5.merkantil.account.error.AccountError;
import github.kaloyanov5.merkantil.account.model.PaymentMethod;
import github.kaloyanov5.merkantil.common.error.AppException;
import github.kaloyanov5.merkantil.common.error.CommonError;
import github.kaloyanov5.merkantil.identity.model.User;
import github.kaloyanov5.merkantil.account.repository.PaymentMethodRepository;
import github.kaloyanov5.merkantil.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    /** Maximum stored (non-deleted) payment methods per user. */
    private static final int MAX_PAYMENT_METHODS_PER_USER = 5;

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;

    public PaymentMethodResponse addPaymentMethod(Long userId, PaymentMethodRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND));

        long existing = paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (existing >= MAX_PAYMENT_METHODS_PER_USER) {
            throw new AppException(AccountError.MAX_PAYMENT_METHODS_REACHED);
        }

        PaymentMethod pm = new PaymentMethod();
        pm.setUser(user);
        pm.setCardholderName(request.cardholderName());
        pm.setLast4(request.last4());
        pm.setExpiryMonth(request.expiryMonth());
        pm.setExpiryYear(request.expiryYear());
        pm.setCardType(request.cardType().toUpperCase());

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
                .orElseThrow(() -> new AppException(AccountError.PAYMENT_METHOD_NOT_FOUND));
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
