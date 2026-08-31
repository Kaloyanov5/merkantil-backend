package github.kaloyanov5.merkantil.common.error;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CommonError implements ErrorCode {
    NOT_AUTHENTICATED("COMM-9001", "You are not authenticated", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("COMM-9002", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    VALIDATION_FAILED("COMM-9003", "The request contains invalid data", HttpStatus.BAD_REQUEST),
    RATE_LIMITED("COMM-9004", "Too many requests. Please try again later", HttpStatus.TOO_MANY_REQUESTS),
    CONFLICT("COMM-9005", "The request conflicts with the current state of the resource", HttpStatus.CONFLICT),
    ACCOUNT_SUSPENDED("COMM-9006", "Your account has been suspended", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND("COMM-9007", "The requested user does not exist", HttpStatus.NOT_FOUND),
    INTERNAL_SERVER_ERROR("COMM-9999", "An unexpected system error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

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
