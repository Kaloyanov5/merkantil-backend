package github.kaloyanov5.merkantil.account.controller.dto.response;

import java.math.BigDecimal;

public record BalanceResponse(
        Long userId,
        BigDecimal balance
) {
}
