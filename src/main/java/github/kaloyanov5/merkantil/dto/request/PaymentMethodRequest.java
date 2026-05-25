package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.*;

public record PaymentMethodRequest(
        @NotBlank(message = "Cardholder name is required")
        String cardholderName,

        @NotBlank(message = "Last 4 digits are required")
        @Pattern(regexp = "\\d{4}", message = "Last 4 must be exactly 4 digits")
        String last4,

        @NotNull(message = "Expiry month is required")
        @Min(value = 1, message = "Invalid expiry month")
        @Max(value = 12, message = "Invalid expiry month")
        Integer expiryMonth,

        @NotNull(message = "Expiry year is required")
        @Min(value = 2025, message = "Card has expired")
        Integer expiryYear,

        @NotBlank(message = "Card type is required")
        String cardType // VISA, MASTERCARD, AMEX, DISCOVER
) {
}
