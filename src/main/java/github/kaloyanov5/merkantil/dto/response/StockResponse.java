package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockResponse(
        Long id,
        String symbol,
        String name,
        String exchange,
        String currency,
        String sector,
        String industry,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        Long volume,
        Double marketCap,
        BigDecimal changeAmount,  // currentPrice - previousClose
        Double changePercent,  // (changeAmount / previousClose) * 100
        BigDecimal extendedHoursPrice,
        Boolean isActive,
        LocalDateTime lastUpdated
) {
}
