package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.*;

import java.time.DateTimeException;
import java.time.YearMonth;

public record PaymentMethodRequest(
        @NotBlank(message = "Cardholder name is required")
        @Size(max = 100, message = "Cardholder name cannot exceed 100 characters")
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
        @Max(value = 2099, message = "Invalid expiry year")
        Integer expiryYear,

        @NotBlank(message = "Card type is required")
        @Pattern(regexp = "^(?i)(VISA|MASTERCARD|AMEX|DISCOVER)$",
                message = "Card type must be VISA, MASTERCARD, AMEX or DISCOVER")
        String cardType
) {
    /**
     * Verifies the expiry is not already in the past, evaluated together with
     * the field-level constraints. Triggered by @Valid at the controller; null
     * year/month are handled by the @NotNull constraints and pass through here.
     */
    @AssertTrue(message = "Card expiry must be in the future")
    public boolean isExpiryInFuture() {
        if (expiryYear == null || expiryMonth == null) return true;
        try {
            return !YearMonth.of(expiryYear, expiryMonth).isBefore(YearMonth.now());
        } catch (DateTimeException e) {
            return false;
        }
    }
}
