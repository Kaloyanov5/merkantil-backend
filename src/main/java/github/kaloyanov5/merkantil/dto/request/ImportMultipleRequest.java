package github.kaloyanov5.merkantil.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for importing multiple stocks by symbol")
public record ImportMultipleRequest(
        @NotEmpty(message = "symbols list must not be empty")
        @Size(max = 100, message = "Maximum 100 symbols per request")
        @Schema(description = "List of stock ticker symbols to import", example = "[\"AAPL\", \"MSFT\", \"GOOGL\"]")
        List<String> symbols
) {
}
