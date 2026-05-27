package github.kaloyanov5.merkantil.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin request body for creating a stock. Only metadata is accepted; prices, volume and timestamps are managed by the platform.")
public record StockAdminRequest(
        @NotBlank(message = "symbol is required")
        @Size(max = 10, message = "symbol must be at most 10 characters")
        @Schema(description = "Ticker symbol (uppercased server-side)", example = "AAPL")
        String symbol,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        @Schema(description = "Company display name", example = "Apple Inc.")
        String name,

        @Size(max = 100)
        @Schema(example = "NASDAQ")
        String exchange,

        @Size(max = 50)
        @Schema(example = "USD")
        String currency,

        @Size(max = 100)
        @Schema(example = "Technology")
        String sector,

        @Size(max = 100)
        @Schema(example = "Consumer Electronics")
        String industry,

        @Schema(description = "Optional active flag; defaults to true on create", example = "true")
        Boolean isActive
) {
}
