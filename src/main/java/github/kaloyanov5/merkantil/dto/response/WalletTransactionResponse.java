package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class WalletTransactionResponse {
    private Long id;
    private String type;       // DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN
    private BigDecimal amount;
    private String cardLast4;  // null if no card used
    private String cardType;   // null if no card used
    private String note;        // counterparty email for transfers
    private String description; // optional user-provided description
    private LocalDateTime timestamp;
}
