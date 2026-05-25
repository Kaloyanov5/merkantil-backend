package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private Long volume;
    private Double marketCap;
    private BigDecimal changeAmount;  // currentPrice - previousClose
    private Double changePercent;  // (changeAmount / previousClose) * 100
    private BigDecimal extendedHoursPrice;
    private Boolean isActive;
    private LocalDateTime lastUpdated;
}
