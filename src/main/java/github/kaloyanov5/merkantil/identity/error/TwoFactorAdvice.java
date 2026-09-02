package github.kaloyanov5.merkantil.identity.error;

import github.kaloyanov5.merkantil.identity.exception.TwoFactorRequiredException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TwoFactorAdvice {

    @ExceptionHandler(TwoFactorRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleTwoFactor(TwoFactorRequiredException e) {
        return ResponseEntity.ok(Map.of(
                "twoFactorRequired", true,
                "tempToken", e.getTempToken()
        ));
    }

}
