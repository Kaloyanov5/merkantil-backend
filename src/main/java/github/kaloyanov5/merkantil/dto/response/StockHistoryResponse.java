package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockHistoryResponse {
    private LocalDate date;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
}