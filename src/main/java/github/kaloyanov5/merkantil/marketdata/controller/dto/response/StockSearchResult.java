package github.kaloyanov5.merkantil.marketdata.controller.dto.response;

public record StockSearchResult(
        String symbol,
        String name,
        String exchange,
        String type  // "Stock", "ETF", etc.
) {
}
