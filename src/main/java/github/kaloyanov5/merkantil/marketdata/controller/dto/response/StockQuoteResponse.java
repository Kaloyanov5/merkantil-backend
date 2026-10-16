package github.kaloyanov5.merkantil.marketdata.controller.dto.response;

import github.kaloyanov5.merkantil.common.constants.MarketSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockQuoteResponse(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal change,
        Double changePercent,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose,
        Long volume,
        BigDecimal extendedHoursPrice,
        BigDecimal extendedHoursChange,
        Double extendedHoursChangePercent,
        String extendedHoursStatus,
        MarketSession marketSession,
        LocalDateTime timestamp
) {
}
