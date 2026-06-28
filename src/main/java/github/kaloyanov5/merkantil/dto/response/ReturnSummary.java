package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;

/** Portfolio value and headline returns. Returns are decimals (0.12 = 12%). */
public record ReturnSummary(
        BigDecimal totalValue,
        double cumulativeReturn,
        double annualizedReturn,
        Double moneyWeightedReturn
) {
}
