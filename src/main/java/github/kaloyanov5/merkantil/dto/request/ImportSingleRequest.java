package github.kaloyanov5.merkantil.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for importing a single stock by symbol")
public record ImportSingleRequest(
        @NotBlank(message = "symbol is required")
        @Size(max = 10, message = "symbol must be at most 10 characters")
        @Pattern(regexp = "^[A-Za-z0-9.\\-]+$", message = "symbol may only contain letters, digits, dot and hyphen")
        @Schema(description = "Stock ticker symbol", example = "AAPL")
        String symbol
) {
}
