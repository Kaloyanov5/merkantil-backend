package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String symbol,
        String side,
        Integer quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        LocalDateTime timestamp
) {
}
