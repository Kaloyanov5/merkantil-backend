package github.kaloyanov5.merkantil.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request body for importing multiple stocks by symbol")
public class ImportMultipleRequest {

    @NotEmpty(message = "symbols list must not be empty")
    @Size(max = 100, message = "Maximum 100 symbols per request")
    @Schema(description = "List of stock ticker symbols to import", example = "[\"AAPL\", \"MSFT\", \"GOOGL\"]")
    private List<String> symbols;
}
