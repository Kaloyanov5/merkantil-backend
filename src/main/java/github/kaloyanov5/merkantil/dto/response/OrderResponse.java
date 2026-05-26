package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String symbol,
        String side,
        Integer quantity,
        BigDecimal executedPrice,
        BigDecimal limitPrice,
        BigDecimal totalAmount,
        String orderType,
        String status,
        LocalDateTime timestamp
) {
}
