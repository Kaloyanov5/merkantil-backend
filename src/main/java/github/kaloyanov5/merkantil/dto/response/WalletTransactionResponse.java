package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long id,
        String type,       // DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN
        BigDecimal amount,
        String cardLast4,  // null if no card used
        String cardType,   // null if no card used
        String note,        // counterparty email for transfers
        String description, // optional user-provided description
        LocalDateTime timestamp
) {
}
