package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioResponse {
    private Long id;
    private String symbol;
    private String stockName;
    private Integer quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal currentValue; // quantity × currentPrice
    private BigDecimal totalCost; // quantity × averageBuyPrice
    private BigDecimal unrealizedGain; // currentValue - totalCost
    private Double unrealizedGainPercent; // (unrealizedGain / totalCost) × 100
}
