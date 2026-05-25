package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank(message = "Stock symbol is required")
        String symbol,

        @NotNull(message = "Order type is required (BUY/SELL)")
        String side, // "BUY" or "SELL"

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        Integer quantity,

        @NotNull(message = "Order type is required (MARKET/LIMIT)")
        String orderType, // "MARKET" or "LIMIT"

        BigDecimal limitPrice // Required if orderType is LIMIT
) {
}
