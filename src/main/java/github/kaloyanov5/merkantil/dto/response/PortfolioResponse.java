package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;

public record PortfolioResponse(
        Long id,
        String symbol,
        String stockName,
        Integer quantity,
        BigDecimal averageBuyPrice,
        BigDecimal currentPrice,
        BigDecimal currentValue, // quantity x currentPrice
        BigDecimal totalCost, // quantity x averageBuyPrice
        BigDecimal unrealizedGain, // currentValue - totalCost
        Double unrealizedGainPercent // (unrealizedGain / totalCost) x 100
) {
}
