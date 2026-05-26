package github.kaloyanov5.merkantil.dto.response;

import java.time.LocalDateTime;

public record PaymentMethodResponse(
        Long id,
        String cardholderName,
        String last4,
        Integer expiryMonth,
        Integer expiryYear,
        String cardType,
        LocalDateTime createdAt
) {
}
