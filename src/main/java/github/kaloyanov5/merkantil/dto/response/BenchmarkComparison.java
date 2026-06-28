package github.kaloyanov5.merkantil.dto.response;

/** Comparison vs the benchmark over the aligned window; numeric fields null when unavailable. */
public record BenchmarkComparison(
        String benchmarkSymbol,
        Double beta,
        Double alpha,
        Double rSquared,
        Double benchmarkReturn,
        Double excessReturn
) {
}
