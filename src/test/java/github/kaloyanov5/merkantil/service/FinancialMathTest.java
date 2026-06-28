package github.kaloyanov5.merkantil.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FinancialMathTest {

    @Test @DisplayName("mean of a known set")
    void mean() {
        assertThat(FinancialMath.mean(new double[]{1, 2, 3, 4})).isEqualTo(2.5);
    }

    @Test @DisplayName("sample stdev (n-1) of the classic 8-point set ≈ 2.138")
    void sampleStdev() {
        double sd = FinancialMath.sampleStdev(new double[]{2, 4, 4, 4, 5, 5, 7, 9});
        assertThat(sd).isCloseTo(2.138, within(0.001));
    }

    @Test @DisplayName("stdev of fewer than 2 points is 0")
    void stdevTooFewPoints() {
        assertThat(FinancialMath.sampleStdev(new double[]{5})).isZero();
    }

    @Test @DisplayName("cumulative compounded return")
    void cumulativeReturn() {
        assertThat(FinancialMath.cumulativeReturn(new double[]{0.1, 0.1}))
                .isCloseTo(0.21, within(1e-9));
    }

    @Test @DisplayName("annualize: one year of returns annualizes to itself")
    void annualize() {
        assertThat(FinancialMath.annualizeReturn(0.10, 252, 252))
                .isCloseTo(0.10, within(1e-9));
    }

    @Test @DisplayName("annualized volatility scales stdev by sqrt(periodsPerYear)")
    void annualizedVolatility() {
        double[] r = {0.01, -0.01, 0.02, -0.02};
        assertThat(FinancialMath.annualizedVolatility(r, 252))
                .isCloseTo(FinancialMath.sampleStdev(r) * Math.sqrt(252), within(1e-9));
    }

    @Test @DisplayName("sharpe for a known set ≈ 6.148")
    void sharpe() {
        Double s = FinancialMath.sharpe(new double[]{0.01, 0.02, -0.01, 0.00}, 0.0, 252);
        assertThat(s).isCloseTo(6.148, within(0.01));
    }

    @Test @DisplayName("sharpe is null when volatility is zero")
    void sharpeZeroVol() {
        assertThat(FinancialMath.sharpe(new double[]{0.01, 0.01, 0.01}, 0.0, 252)).isNull();
    }

    @Test @DisplayName("sortino for a known set ≈ 15.87")
    void sortino() {
        Double s = FinancialMath.sortino(new double[]{0.01, 0.02, -0.01, 0.00}, 0.0, 252);
        assertThat(s).isCloseTo(15.87, within(0.05));
    }

    @Test @DisplayName("max drawdown magnitude")
    void maxDrawdown() {
        assertThat(FinancialMath.maxDrawdown(new double[]{0.1, -0.2, 0.05}))
                .isCloseTo(0.20, within(1e-9));
    }

    @Test @DisplayName("beta is 1.0 against an identical series and 2.0 against half-scaled")
    void beta() {
        double[] bench = {0.02, 0.01, -0.01, 0.03};
        double[] same = {0.02, 0.01, -0.01, 0.03};
        double[] doubled = {0.04, 0.02, -0.02, 0.06};
        assertThat(FinancialMath.beta(same, bench)).isCloseTo(1.0, within(1e-9));
        assertThat(FinancialMath.beta(doubled, bench)).isCloseTo(2.0, within(1e-9));
    }

    @Test @DisplayName("beta is null when benchmark variance is zero")
    void betaZeroVar() {
        assertThat(FinancialMath.beta(new double[]{0.01, 0.02}, new double[]{0.01, 0.01})).isNull();
    }

    @Test @DisplayName("correlation of identical series is 1.0")
    void correlation() {
        double[] x = {0.02, 0.01, -0.01, 0.03};
        assertThat(FinancialMath.correlation(x, x)).isCloseTo(1.0, within(1e-9));
    }

    @Test @DisplayName("jensen's alpha")
    void jensensAlpha() {
        assertThat(FinancialMath.jensensAlpha(0.12, 0.10, 1.0, 0.02))
                .isCloseTo(0.02, within(1e-9));
    }

    @Test @DisplayName("xirr: -1000 today, +1100 in 365 days ≈ 10%")
    void xirr() {
        Double r = FinancialMath.xirr(new double[]{-1000, 1100}, new long[]{0, 365});
        assertThat(r).isCloseTo(0.10, within(1e-3));
    }

    @Test @DisplayName("xirr returns null for single flow")
    void xirrTooFew() {
        assertThat(FinancialMath.xirr(new double[]{-1000}, new long[]{0})).isNull();
    }
}
