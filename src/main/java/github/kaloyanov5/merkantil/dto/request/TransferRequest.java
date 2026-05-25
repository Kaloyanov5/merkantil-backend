package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Recipient email should be valid")
        String recipientEmail,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @Size(max = 200, message = "Description must not exceed 200 characters")
        String description
) {
}
