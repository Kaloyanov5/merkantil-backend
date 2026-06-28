package github.kaloyanov5.merkantil.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/** Transparency block describing how much data backed the computation. */
public record DataQuality(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        int dataPoints,
        boolean lowConfidence,
        List<String> excludedSymbols
) {
}
