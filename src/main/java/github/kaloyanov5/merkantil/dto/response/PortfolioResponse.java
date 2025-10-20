package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioResponse {
    private Long id;
    private String symbol;
    private String stockName;
    private Integer quantity;
    private Double averageBuyPrice;
    private Double currentPrice;
    private Double currentValue; // quantity × currentPrice
    private Double totalCost; // quantity × averageBuyPrice
    private Double unrealizedGain; // currentValue - totalCost
    private Double unrealizedGainPercent; // (unrealizedGain / totalCost) × 100
}
