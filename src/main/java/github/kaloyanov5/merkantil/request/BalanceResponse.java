package github.kaloyanov5.merkantil.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BalanceResponse {
    private Long userId;
    private Double balance;
}
