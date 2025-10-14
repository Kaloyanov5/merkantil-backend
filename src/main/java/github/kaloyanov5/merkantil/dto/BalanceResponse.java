package github.kaloyanov5.merkantil.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BalanceResponse {
    private Long userId;
    private Double balance;
}
