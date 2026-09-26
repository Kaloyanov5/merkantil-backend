package github.kaloyanov5.merkantil.common.ratelimit;

import github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimited;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
@RequiredArgsConstructor
@Order(20)
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @Around(
            "@annotation(github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimited) " +
            "|| @annotation(github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimits)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        EvaluationContext context = new MethodBasedEvaluationContext(
                null, method, joinPoint.getArgs(), PARAMETER_NAMES);

        RateLimited[] annotations = method.getAnnotationsByType(RateLimited.class);

        for (RateLimited rateLimited : annotations) {
            String key = expressionCache.computeIfAbsent(
                    rateLimited.key(), PARSER::parseExpression).getValue(context, String.class);

            if (Objects.isNull(key)) {
                continue;
            }

            rateLimiterService.enforce(
                    rateLimited.bucket() + ":" + key,
                    rateLimited.limit(),
                    Duration.of(rateLimited.duration(), rateLimited.unit().toChronoUnit())
            );
        }

        return joinPoint.proceed();
    }
}
