package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PaymentMethodRequest {

    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;

    @NotBlank(message = "Last 4 digits are required")
    @Pattern(regexp = "\\d{4}", message = "Last 4 must be exactly 4 digits")
    private String last4;

    @NotNull(message = "Expiry month is required")
    @Min(value = 1, message = "Invalid expiry month")
    @Max(value = 12, message = "Invalid expiry month")
    private Integer expiryMonth;

    @NotNull(message = "Expiry year is required")
    @Min(value = 2025, message = "Card has expired")
    private Integer expiryYear;

    @NotBlank(message = "Card type is required")
    private String cardType; // VISA, MASTERCARD, AMEX, DISCOVER
}
