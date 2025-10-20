package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OrderRequest {
    @NotBlank(message = "Stock symbol is required")
    private String symbol;

    @NotNull(message = "Order type is required (BUY/SELL)")
    private String side; // "BUY" or "SELL"

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotNull(message = "Order type is required (MARKET/LIMIT)")
    private String orderType; // "MARKET" or "LIMIT"

    private Double limitPrice; // Required if orderType is LIMIT
}