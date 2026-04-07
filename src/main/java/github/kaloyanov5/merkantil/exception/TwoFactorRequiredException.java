package github.kaloyanov5.merkantil.exception;

import lombok.Getter;

@Getter
public class TwoFactorRequiredException extends RuntimeException {

    private final String tempToken;

    public TwoFactorRequiredException(String tempToken) {
        super("2FA required");
        this.tempToken = tempToken;
    }
}
