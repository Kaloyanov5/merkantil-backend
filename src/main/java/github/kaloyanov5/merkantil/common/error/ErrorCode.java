package github.kaloyanov5.merkantil.common.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String getCode();
    HttpStatus getStatus();
    String getMessage();
}
