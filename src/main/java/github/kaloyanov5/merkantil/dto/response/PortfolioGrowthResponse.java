package github.kaloyanov5.merkantil.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Portfolio value for a specific trading day
 * This represents reconstructed historical portfolio value
 */
public record PortfolioGrowthResponse(
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,
        Double value
) {}

