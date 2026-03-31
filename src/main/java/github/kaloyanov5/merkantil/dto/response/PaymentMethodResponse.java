package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PaymentMethodResponse {
    private Long id;
    private String cardholderName;
    private String last4;
    private Integer expiryMonth;
    private Integer expiryYear;
    private String cardType;
    private LocalDateTime createdAt;
}
