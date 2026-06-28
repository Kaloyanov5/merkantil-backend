package github.kaloyanov5.merkantil.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Pure conversion of a date→close map into a daily simple-return series aligned to
 * a list of trading days. For each consecutive pair it uses the most recent close
 * on or before each day ({@code floorEntry}); a pair is skipped when no prior close
 * exists for either side.
 */
public final class PriceSeries {

    private PriceSeries() {
    }

    public static List<DailyReturn> dailyReturns(NavigableMap<LocalDate, BigDecimal> closes,
                                                 List<LocalDate> tradingDays) {
        List<DailyReturn> out = new ArrayList<>();
        for (int i = 1; i < tradingDays.size(); i++) {
            LocalDate prev = tradingDays.get(i - 1);
            LocalDate cur = tradingDays.get(i);
            Map.Entry<LocalDate, BigDecimal> ePrev = closes.floorEntry(prev);
            Map.Entry<LocalDate, BigDecimal> eCur = closes.floorEntry(cur);
            if (ePrev == null || eCur == null) continue;
            double p = ePrev.getValue().doubleValue();
            double c = eCur.getValue().doubleValue();
            if (p == 0.0) continue;
            out.add(new DailyReturn(cur, c / p - 1.0));
        }
        return out;
    }
}
