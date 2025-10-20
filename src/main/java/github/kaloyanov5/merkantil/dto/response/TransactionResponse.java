package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
    private Long id;
    private String symbol;
    private String side;
    private Integer quantity;
    private Double price;
    private Double totalAmount;
    private LocalDateTime timestamp;
}
