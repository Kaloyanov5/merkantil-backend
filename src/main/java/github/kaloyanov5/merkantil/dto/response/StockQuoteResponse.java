package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockQuoteResponse {
    private String symbol;
    private String name;
    private Double price;
    private Double change;
    private Double changePercent;
    private Double high;
    private Double low;
    private Double open;
    private Double previousClose;
    private Long volume;
    private LocalDateTime timestamp;
}