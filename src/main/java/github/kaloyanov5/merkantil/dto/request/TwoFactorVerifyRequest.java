package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorVerifyRequest {

    @NotBlank(message = "Session token is required")
    private String tempToken;

    @NotBlank(message = "Code is required")
    private String code;
}
