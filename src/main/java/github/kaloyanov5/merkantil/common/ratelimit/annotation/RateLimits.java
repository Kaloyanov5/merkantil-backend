package github.kaloyanov5.merkantil.common.ratelimit.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimits {
    RateLimited[] value();
}
