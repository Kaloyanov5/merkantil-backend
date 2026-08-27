package github.kaloyanov5.merkantil.common.ratelimit;

import lombok.Getter;

@Getter
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super("Too many attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
