package github.kaloyanov5.merkantil.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters long")
        String newPassword,

        @NotBlank(message = "Please confirm your new password")
        String confirmNewPassword
) {
    @AssertTrue(message = "New password and confirmation do not match")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isPasswordConfirmationValid() {
        return newPassword != null && newPassword.equals(confirmNewPassword);
    }
}
