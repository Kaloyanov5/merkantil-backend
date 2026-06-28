package github.kaloyanov5.merkantil.service;

/**
 * Pure statistical helpers for portfolio analytics. No Spring, no I/O.
 * Inputs are arrays of period (daily) returns expressed as decimals (0.01 = 1%).
 * Methods that can divide by zero or need ≥2 observations return a nullable
 * {@link Double} and yield {@code null} when undefined.
 */
public final class FinancialMath {

    private FinancialMath() {
    }

    public static double mean(double[] xs) {
        if (xs.length == 0) return 0.0;
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    /** Sample standard deviation (n-1). Returns 0 for fewer than 2 points. */
    public static double sampleStdev(double[] xs) {
        if (xs.length < 2) return 0.0;
        double m = mean(xs), s = 0;
        for (double x : xs) s += (x - m) * (x - m);
        return Math.sqrt(s / (xs.length - 1));
    }

    /** Sample covariance (n-1). Returns 0 for mismatched lengths or < 2 points. */
    public static double sampleCovariance(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length < 2) return 0.0;
        double mx = mean(xs), my = mean(ys), s = 0;
        for (int i = 0; i < xs.length; i++) s += (xs[i] - mx) * (ys[i] - my);
        return s / (xs.length - 1);
    }

    /** Pearson correlation, or null when undefined (zero variance / < 2 points). */
    public static Double correlation(double[] xs, double[] ys) {
        double sx = sampleStdev(xs), sy = sampleStdev(ys);
        if (sx == 0.0 || sy == 0.0) return null;
        return sampleCovariance(xs, ys) / (sx * sy);
    }

    /** Cumulative compounded return from a series of period returns. */
    public static double cumulativeReturn(double[] periodReturns) {
        double p = 1.0;
        for (double r : periodReturns) p *= (1.0 + r);
        return p - 1.0;
    }

    public static double annualizeReturn(double cumulativeReturn, int periods, int periodsPerYear) {
        if (periods <= 0) return 0.0;
        return Math.pow(1.0 + cumulativeReturn, (double) periodsPerYear / periods) - 1.0;
    }

    public static double annualizedVolatility(double[] periodReturns, int periodsPerYear) {
        return sampleStdev(periodReturns) * Math.sqrt(periodsPerYear);
    }

    /** Annualized Sharpe; null when volatility is zero or < 2 points. */
    public static Double sharpe(double[] dailyReturns, double riskFreeAnnual, int periodsPerYear) {
        if (dailyReturns.length < 2) return null;
        double rfDaily = riskFreeAnnual / periodsPerYear;
        double[] excess = new double[dailyReturns.length];
        for (int i = 0; i < dailyReturns.length; i++) excess[i] = dailyReturns[i] - rfDaily;
        double sd = sampleStdev(excess);
        if (sd == 0.0) return null;
        return (mean(excess) / sd) * Math.sqrt(periodsPerYear);
    }

    /** Annualized Sortino; null when downside deviation is zero or < 2 points. */
    public static Double sortino(double[] dailyReturns, double riskFreeAnnual, int periodsPerYear) {
        if (dailyReturns.length < 2) return null;
        double rfDaily = riskFreeAnnual / periodsPerYear;
        double meanExcess = 0;
        for (double r : dailyReturns) meanExcess += (r - rfDaily);
        meanExcess /= dailyReturns.length;
        double sumSqDown = 0;
        for (double r : dailyReturns) {
            double d = Math.min(r - rfDaily, 0.0);
            sumSqDown += d * d;
        }
        double downside = Math.sqrt(sumSqDown / dailyReturns.length);
        if (downside == 0.0) return null;
        return (meanExcess / downside) * Math.sqrt(periodsPerYear);
    }

    /** Maximum drawdown magnitude (positive fraction) over a daily return series. */
    public static double maxDrawdown(double[] dailyReturns) {
        double wealth = 1.0, peak = 1.0, maxDd = 0.0;
        for (double r : dailyReturns) {
            wealth *= (1.0 + r);
            if (wealth > peak) peak = wealth;
            double dd = (peak - wealth) / peak;
            if (dd > maxDd) maxDd = dd;
        }
        return maxDd;
    }

    /** Beta of asset vs benchmark; null for mismatched lengths, < 2 points, or zero benchmark variance. */
    public static Double beta(double[] asset, double[] benchmark) {
        if (asset.length != benchmark.length || asset.length < 2) return null;
        double varB = sampleCovariance(benchmark, benchmark);
        if (varB == 0.0) return null;
        return sampleCovariance(asset, benchmark) / varB;
    }

    public static double jensensAlpha(double portfolioAnnualReturn, double benchmarkAnnualReturn,
                                      double beta, double riskFreeAnnual) {
        return portfolioAnnualReturn - (riskFreeAnnual + beta * (benchmarkAnnualReturn - riskFreeAnnual));
    }

    /**
     * Annualized internal rate of return for dated cash flows (XIRR).
     * {@code amounts[i]} occurs {@code dayOffsets[i]} days after the first flow.
     * Newton-Raphson with a bisection fallback on [-0.9999, 10]. Null if it cannot converge.
     */
    public static Double xirr(double[] amounts, long[] dayOffsets) {
        if (amounts.length != dayOffsets.length || amounts.length < 2) return null;

        double rate = 0.1;
        for (int iter = 0; iter < 100; iter++) {
            double npv = 0, dnpv = 0;
            boolean bad = false;
            for (int i = 0; i < amounts.length; i++) {
                double base = 1.0 + rate;
                if (base <= 0) {
                    bad = true;
                    break;
                }
                double t = dayOffsets[i] / 365.0;
                npv += amounts[i] / Math.pow(base, t);
                dnpv += -t * amounts[i] / Math.pow(base, t + 1);
            }
            if (bad || dnpv == 0) break;
            double next = rate - npv / dnpv;
            if (Math.abs(next - rate) < 1e-7) return next;
            rate = next;
        }

        double lo = -0.9999, hi = 10.0;
        double fLo = npvAt(amounts, dayOffsets, lo);
        double fHi = npvAt(amounts, dayOffsets, hi);
        if (Double.isNaN(fLo) || Double.isNaN(fHi) || fLo * fHi > 0) return null;
        for (int iter = 0; iter < 200; iter++) {
            double mid = (lo + hi) / 2.0;
            double fMid = npvAt(amounts, dayOffsets, mid);
            if (Math.abs(fMid) < 1e-7) return mid;
            if (fLo * fMid < 0) {
                hi = mid;
            } else {
                lo = mid;
                fLo = fMid;
            }
        }
        return (lo + hi) / 2.0;
    }

    private static double npvAt(double[] amounts, long[] dayOffsets, double rate) {
        double base = 1.0 + rate;
        if (base <= 0) return Double.NaN;
        double npv = 0;
        for (int i = 0; i < amounts.length; i++) npv += amounts[i] / Math.pow(base, dayOffsets[i] / 365.0);
        return npv;
    }
}
