package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LoginSessionResponse {
    private String sessionId;
    private String ip;
    private String deviceInfo;
    private LocalDateTime createdAt;
    private boolean current;
}
