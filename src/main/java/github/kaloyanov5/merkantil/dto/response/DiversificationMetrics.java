package github.kaloyanov5.merkantil.dto.response;

import java.util.Map;

/** Concentration / spread of current positions. Weights are fractions of total value. */
public record DiversificationMetrics(
        Map<String, Double> sectorAllocation,
        double herfindahlIndex,
        double effectiveHoldings,
        double topHoldingWeight,
        double topThreeWeight
) {
}
