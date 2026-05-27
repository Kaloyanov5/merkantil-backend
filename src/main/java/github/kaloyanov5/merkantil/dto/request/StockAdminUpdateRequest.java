package github.kaloyanov5.merkantil.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin request body for partially updating a stock. All fields optional; non-null fields are applied.")
public record StockAdminUpdateRequest(
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Size(max = 100)
        String exchange,

        @Size(max = 50)
        String currency,

        @Size(max = 100)
        String sector,

        @Size(max = 100)
        String industry,

        Boolean isActive
) {
}
