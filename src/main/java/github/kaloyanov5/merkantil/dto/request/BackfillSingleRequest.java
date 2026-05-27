package github.kaloyanov5.merkantil.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "Request body for backfilling historical data for a single stock")
public record BackfillSingleRequest(
        @NotBlank(message = "symbol is required")
        @Size(max = 10, message = "symbol must be at most 10 characters")
        @Pattern(regexp = "^[A-Za-z0-9.\\-]+$", message = "symbol may only contain letters, digits, dot and hyphen")
        @Schema(description = "Stock ticker symbol", example = "AAPL")
        String symbol,

        @NotNull(message = "startDate is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "Inclusive start date (ISO yyyy-MM-dd)", example = "2024-01-01")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "Inclusive end date (ISO yyyy-MM-dd)", example = "2024-12-31")
        LocalDate endDate
) {
}
