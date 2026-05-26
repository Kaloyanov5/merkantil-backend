package github.kaloyanov5.merkantil.dto.response;

import java.time.LocalDateTime;

public record LoginSessionResponse(
        String sessionId,
        String ip,
        String deviceInfo,
        LocalDateTime createdAt,
        boolean current
) {
}
