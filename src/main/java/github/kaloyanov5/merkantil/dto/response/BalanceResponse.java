package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;

public record BalanceResponse(
        Long userId,
        BigDecimal balance
) {
}
