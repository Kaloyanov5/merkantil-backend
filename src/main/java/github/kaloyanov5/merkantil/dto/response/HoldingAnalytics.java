package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;

/** Per-holding analytics. Statistical fields are null when history is insufficient. */
public record HoldingAnalytics(
        String symbol,
        int quantity,
        BigDecimal marketValue,
        double weight,
        Double annualizedVolatility,
        Double beta,
        Double contributionToReturn,
        BigDecimal unrealizedGain
) {
}
