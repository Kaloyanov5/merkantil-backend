package github.kaloyanov5.merkantil.common.constants;

import java.util.Set;

public enum MarketSession {
    OPEN,
    CLOSED,
    PRE_MARKET,
    AFTER_HOURS,
    HOLIDAY;

    public static final Set<MarketSession> EXTENDED_HOURS = Set.of(PRE_MARKET, AFTER_HOURS);
    public static final Set<MarketSession> NON_TRADABLE = Set.of(CLOSED, HOLIDAY);
}
