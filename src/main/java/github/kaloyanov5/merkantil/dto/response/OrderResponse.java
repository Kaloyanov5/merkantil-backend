package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private Long id;
    private String symbol;
    private String side;
    private Integer quantity;
    private Double executedPrice;
    private Double totalAmount;
    private String orderType;
    private String status;
    private LocalDateTime timestamp;
}