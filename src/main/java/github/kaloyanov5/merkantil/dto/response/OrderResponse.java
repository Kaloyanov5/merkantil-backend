package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private Long id;
    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal executedPrice;
    private BigDecimal limitPrice;
    private BigDecimal totalAmount;
    private String orderType;
    private String status;
    private LocalDateTime timestamp;
}
