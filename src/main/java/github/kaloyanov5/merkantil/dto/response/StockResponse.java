package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockResponse {
    private Long id;
    private String symbol;
    private String name;
    private String exchange;
    private String currency;
    private String sector;
    private String industry;
    private Double currentPrice;
    private Double previousClose;
    private Double dayHigh;
    private Double dayLow;
    private Long volume;
    private Double marketCap;
    private Double changeAmount;  // currentPrice - previousClose
    private Double changePercent;  // (changeAmount / previousClose) * 100
    private Double extendedHoursPrice;
    private Boolean isActive;
    private LocalDateTime lastUpdated;
}