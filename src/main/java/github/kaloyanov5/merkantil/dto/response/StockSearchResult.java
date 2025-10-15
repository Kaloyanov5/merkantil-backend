package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockSearchResult {
    private String symbol;
    private String name;
    private String exchange;
    private String type;  // "Stock", "ETF", etc.
}