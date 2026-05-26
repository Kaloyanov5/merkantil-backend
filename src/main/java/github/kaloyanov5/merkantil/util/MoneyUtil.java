package github.kaloyanov5.merkantil.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money and price arithmetic helpers. Every monetary value in the domain is a
 * {@link BigDecimal} with a fixed scale of {@value #SCALE} and
 * {@link RoundingMode#HALF_UP} rounding, matching the {@code DECIMAL(19,4)}
 * database columns.
 *
 * <p>The Massive market-data API returns prices as primitive {@code double}.
 * Those values cross into the domain exclusively through {@link #of(Double)} —
 * the single sanctioned {@code double -> BigDecimal} conversion point. Doing
 * the conversion anywhere else risks carrying binary floating-point error into
 * stored balances and order amounts.
 */
public final class MoneyUtil {

    /** Decimal places kept for every monetary value (matches {@code DECIMAL(19,4)}). */
    public static final int SCALE = 4;

    /** Rounding applied to every monetary result. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtil() {
    }

    /**
     * Converts a nullable {@code double} from an external source (the Massive
     * API) into a scaled {@link BigDecimal}. Returns {@code null} for null input.
     */
    public static BigDecimal of(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    /** Rescales a {@link BigDecimal} to the money scale, or returns {@code null}. */
    public static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING);
    }

    /** Multiplies a per-share price by an integer quantity. */
    public static BigDecimal multiply(BigDecimal price, int quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING);
    }

    /** Divides with the money scale and rounding — safe for non-terminating results. */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, SCALE, ROUNDING);
    }

    /**
     * Divides with banker's rounding (HALF_EVEN) at the money scale. Use for
     * averaging / cost-basis computations where repeated HALF_UP rounding
     * would introduce a small statistical bias in the user's favor (lower
     * reported gain on sale). HALF_EVEN rounds ties to the nearest even
     * digit, which is bias-free over many operations.
     */
    public static BigDecimal divideHalfEven(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, SCALE, RoundingMode.HALF_EVEN);
    }

    /** True when {@code value} is non-null and strictly greater than zero. */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
