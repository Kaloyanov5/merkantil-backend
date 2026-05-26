package github.kaloyanov5.merkantil.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of POST /api/auth/2fa/enable and /disable. Requires the user to
 * re-enter their current password so a stolen session cookie alone (XSS,
 * physical access to an unlocked browser) cannot silently toggle 2FA.
 */
public record TwoFactorToggleRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword
) {
}
