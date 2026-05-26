package github.kaloyanov5.merkantil.dto.response;

public record AuthResponse(
        String message,
        UserResponse user
) {
}
