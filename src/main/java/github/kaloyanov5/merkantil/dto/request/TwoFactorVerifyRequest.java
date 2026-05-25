package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "Session token is required")
        String tempToken,

        @NotBlank(message = "Code is required")
        String code
) {
}
