package github.kaloyanov5.merkantil.ratelimit;

import github.kaloyanov5.merkantil.common.ratelimit.RateLimitAspect;
import github.kaloyanov5.merkantil.common.ratelimit.RateLimitedException;
import github.kaloyanov5.merkantil.common.ratelimit.RateLimiterService;
import github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimited;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RateLimiterService rateLimiterService;

    private Target target;
    private Target proxy;

    @BeforeEach
    void setUp() {
        target = new Target();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RateLimitAspect(rateLimiterService));
        proxy = factory.getProxy();
    }

    @Test
    @DisplayName("key is evaluated as SpEL against the arguments, not passed through literally")
    void singleLimit_evaluatesKeyAgainstArguments() {
        proxy.register("1.2.3.4");

        verify(rateLimiterService).enforce("register:1.2.3.4", 5, Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("a non-String argument is stringified into the key")
    void nonStringArgument_isStringified() {
        proxy.transfer(42L);

        verify(rateLimiterService).enforce("transfer:42", 10, Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("repeatable: both limits are enforced, in declaration order")
    void repeatedLimits_bothEnforced() {
        proxy.forgotPassword("User@Example.COM", "1.2.3.4");

        InOrder ordered = inOrder(rateLimiterService);
        ordered.verify(rateLimiterService).enforce("forgot:user@example.com", 3, Duration.ofMinutes(60));
        ordered.verify(rateLimiterService).enforce("forgot-ip:1.2.3.4", 10, Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("a key resolving to null skips enforcement instead of sharing one bucket")
    void nullKey_skipsEnforcement() {
        String result = proxy.register(null);

        verifyNoInteractions(rateLimiterService);
        assertThat(result).isEqualTo("registered");
        assertThat(target.registerCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("a null key suppresses only its own limit, not the others on the method")
    void nullKey_suppressesOnlyThatLimit() {
        proxy.forgotPassword("user@example.com", null);

        verify(rateLimiterService).enforce("forgot:user@example.com", 3, Duration.ofMinutes(60));
        verify(rateLimiterService, never()).enforce(eq("forgot-ip:null"), anyInt(), any());
        verify(rateLimiterService, never()).enforce(eq("forgot-ip:"), anyInt(), any());
    }

    @Test
    @DisplayName("the unit attribute is honoured")
    void unitAttribute_isHonoured() {
        proxy.verifyEmail("1.2.3.4");

        verify(rateLimiterService).enforce("verify-email:1.2.3.4", 10, Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("a rejected request never reaches the method body")
    void rejection_shortCircuitsTheTarget() {
        doThrow(new RateLimitedException(600L))
                .when(rateLimiterService).enforce(eq("register:1.2.3.4"), anyInt(), any());

        assertThatThrownBy(() -> proxy.register("1.2.3.4"))
                .isInstanceOf(RateLimitedException.class)
                .extracting("retryAfterSeconds").isEqualTo(600L);

        assertThat(target.registerCalls).isZero();
    }

    @Test
    @DisplayName("an unannotated method is not advised")
    void unannotatedMethod_isNotAdvised() {
        proxy.unannotated();

        verifyNoInteractions(rateLimiterService);
    }

    public static class Target {

        int registerCalls;

        @RateLimited(bucket = "register", key = "#clientIp", limit = 5, duration = 60)
        public String register(String clientIp) {
            registerCalls++;
            return "registered";
        }

        @RateLimited(bucket = "transfer", key = "#senderId", limit = 10, duration = 1)
        public String transfer(Long senderId) {
            return "transferred";
        }

        @RateLimited(bucket = "forgot", key = "#email.toLowerCase()", limit = 3, duration = 60)
        @RateLimited(bucket = "forgot-ip", key = "#clientIp", limit = 10, duration = 60)
        public void forgotPassword(String email, String clientIp) {
        }

        @RateLimited(bucket = "verify-email", key = "#clientIp", limit = 10, duration = 30, unit = TimeUnit.SECONDS)
        public void verifyEmail(String clientIp) {
        }

        public String unannotated() {
            return "ok";
        }
    }
}
