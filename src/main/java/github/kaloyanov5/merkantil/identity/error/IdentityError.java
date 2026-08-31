package github.kaloyanov5.merkantil.identity.error;

import github.kaloyanov5.merkantil.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum IdentityError implements ErrorCode {
    EMAIL_ALREADY_EXISTS("IDNT-1001", "This email is already registered", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("IDNT-1002", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    INVALID_SESSION("IDNT-1003", "Invalid or expired session, please log in again", HttpStatus.UNAUTHORIZED),
    INVALID_TWO_FACTOR_CODE("IDNT-1004", "Invalid or expired 2FA code", HttpStatus.BAD_REQUEST),
    INVALID_RESET_CODE("IDNT-1005", "Invalid or expired reset code", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_TOKEN("IDNT-1006", "Invalid or expired verification token", HttpStatus.BAD_REQUEST),
    SESSION_NOT_FOUND("IDNT-1007", "Session not found", HttpStatus.NOT_FOUND),
    INVALID_REQUEST_DATA("IDNT-1008", "Invalid request data", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
