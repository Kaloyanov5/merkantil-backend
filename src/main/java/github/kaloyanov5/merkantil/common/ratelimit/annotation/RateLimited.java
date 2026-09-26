package github.kaloyanov5.merkantil.common.ratelimit.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimited {
    String bucket();

    String key();

    int limit();

    long duration();

    TimeUnit unit() default TimeUnit.MINUTES;
}
