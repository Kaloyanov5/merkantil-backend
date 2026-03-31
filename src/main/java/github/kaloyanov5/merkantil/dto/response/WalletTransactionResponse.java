package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class WalletTransactionResponse {
    private Long id;
    private String type;       // DEPOSIT or WITHDRAWAL
    private BigDecimal amount;
    private String cardLast4;  // null if no card used
    private String cardType;   // null if no card used
    private LocalDateTime timestamp;
}
