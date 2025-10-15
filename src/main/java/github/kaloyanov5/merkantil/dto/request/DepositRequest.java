package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DepositRequest {
    @Positive(message = "Amount must be positive")
    private Double amount;
}
