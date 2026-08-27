package github.kaloyanov5.merkantil.identity.controller.dto.response;

public record AuthResponse(
        String message,
        UserResponse user
) {
}
