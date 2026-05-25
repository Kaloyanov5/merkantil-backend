package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockQuoteResponse {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private Double changePercent;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal open;
    private BigDecimal previousClose;
    private Long volume;
    private BigDecimal extendedHoursPrice;
    private BigDecimal extendedHoursChange;
    private Double extendedHoursChangePercent;
    private String marketSession;
    private LocalDateTime timestamp;
}
