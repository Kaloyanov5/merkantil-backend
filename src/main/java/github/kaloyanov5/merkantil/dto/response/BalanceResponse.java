package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class BalanceResponse {
    private Long userId;
    private BigDecimal balance;
}
