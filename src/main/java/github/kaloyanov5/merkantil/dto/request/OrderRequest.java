package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank(message = "Stock symbol is required")
        @Size(max = 10, message = "Stock symbol must be at most 10 characters")
        @Pattern(regexp = "^[A-Z][A-Z.]{0,9}$", message = "Stock symbol must be uppercase letters and dots only")
        String symbol,

        @NotNull(message = "Side is required (BUY/SELL)")
        @Pattern(regexp = "^(BUY|SELL)$", message = "Side must be BUY or SELL")
        String side,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        @Max(value = 1_000_000, message = "Quantity cannot exceed 1,000,000 shares per order")
        Integer quantity,

        @NotNull(message = "Order type is required (MARKET/LIMIT)")
        @Pattern(regexp = "^(MARKET|LIMIT)$", message = "Order type must be MARKET or LIMIT")
        String orderType,

        @DecimalMax(value = "100000000.0000", message = "Limit price is unreasonably large")
        BigDecimal limitPrice // Required if orderType is LIMIT
) {
}
