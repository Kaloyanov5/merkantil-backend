package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequest {
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Recipient email should be valid")
    private String recipientEmail;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}
