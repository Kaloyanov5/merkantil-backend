package github.kaloyanov5.merkantil.service;

import java.util.List;

/** Portfolio daily return series plus any symbols excluded for missing price data. */
public record PortfolioReturnSeries(List<DailyReturn> dailyReturns, List<String> excludedSymbols) {
}
