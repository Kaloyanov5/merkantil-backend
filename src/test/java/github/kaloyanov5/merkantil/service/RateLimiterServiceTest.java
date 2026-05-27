package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.exception.RateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimiterServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private RateLimiterService rateLimiterService;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Empty fail-closed list keeps existing in-memory fallback behavior under test
        rateLimiterService = new RateLimiterService(redisTemplate, List.of());
    }

    // ---------- THROUGHPUT LIMITING (enforce) ----------

    @Test
    @DisplayName("enforce: below the limit does not throw")
    void enforce_belowLimit_passes() {
        when(valueOps.increment("ratelimit:order:1")).thenReturn(3L);

        assertThatCode(() -> rateLimiterService.enforce("order:1", 10, WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enforce: exceeding the limit throws RateLimitedException")
    void enforce_overLimit_throws() {
        when(valueOps.increment("ratelimit:order:1")).thenReturn(11L);

        assertThatThrownBy(() -> rateLimiterService.enforce("order:1", 10, WINDOW))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("enforce: first hit sets the window TTL")
    void enforce_firstHit_setsTtl() {
        when(valueOps.increment("ratelimit:order:1")).thenReturn(1L);

        rateLimiterService.enforce("order:1", 10, WINDOW);

        verify(redisTemplate).expire("ratelimit:order:1", WINDOW);
    }

    // ---------- ATTEMPT LIMITING (check) ----------

    @Test
    @DisplayName("check: at the threshold throws RateLimitedException")
    void check_atThreshold_throws() {
        when(valueOps.get("ratelimit:login:user@example.com")).thenReturn("5");

        assertThatThrownBy(() -> rateLimiterService.check("login:user@example.com", 5, WINDOW))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("check: below the threshold passes")
    void check_belowThreshold_passes() {
        when(valueOps.get("ratelimit:login:user@example.com")).thenReturn("2");

        assertThatCode(() -> rateLimiterService.check("login:user@example.com", 5, WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("clear: deletes the prefixed counter key")
    void clear_deletesKey() {
        rateLimiterService.clear("login:user@example.com");

        verify(redisTemplate).delete("ratelimit:login:user@example.com");
    }

    // ---------- REDIS-DOWN FALLBACK ----------

    @Test
    @DisplayName("enforce: falls back to in-memory counting when Redis is unreachable")
    void enforce_redisDown_fallsBackToMemory() {
        when(valueOps.increment(any()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        // First 10 hits are allowed by the in-memory fallback counter
        for (int i = 0; i < 10; i++) {
            int hit = i + 1;
            assertThatCode(() -> rateLimiterService.enforce("order:99", 10, WINDOW))
                    .as("hit %d should be allowed", hit)
                    .doesNotThrowAnyException();
        }

        // The 11th hit exceeds the limit, still enforced without Redis
        assertThatThrownBy(() -> rateLimiterService.enforce("order:99", 10, WINDOW))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("enforce: fail-closed prefix denies request when Redis is unreachable")
    void enforce_failClosedPrefix_deniesOnRedisOutage() {
        RateLimiterService failClosed = new RateLimiterService(redisTemplate, List.of("login:"));
        when(valueOps.increment(any()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertThatThrownBy(() -> failClosed.enforce("login:user@example.com", 5, WINDOW))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("check: fail-closed prefix denies request when Redis is unreachable")
    void check_failClosedPrefix_deniesOnRedisOutage() {
        RateLimiterService failClosed = new RateLimiterService(redisTemplate, List.of("password-reset:"));
        when(valueOps.get(any()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertThatThrownBy(() -> failClosed.check("password-reset:user@example.com", 3, WINDOW))
                .isInstanceOf(RateLimitedException.class);
    }
}
