package github.kaloyanov5.merkantil.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        BigDecimal balance,
        LocalDateTime createdAt,
        Boolean emailVerified,
        Boolean twoFactorEnabled,
        Boolean banned
) {
}
