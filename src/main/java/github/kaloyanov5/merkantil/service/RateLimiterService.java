package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.exception.RateLimitedException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Centralized rate limiting backed by a Redis counter, with an in-memory
 * fallback that engages automatically whenever Redis is unreachable. A Redis
 * outage therefore degrades rate limiting to per-instance counting instead of
 * failing the request — except for key prefixes listed in
 * {@code ratelimit.fail-closed-prefixes}, which fail closed (deny) when Redis
 * is unreachable. Sensitive flows such as login, 2FA and password reset should
 * be enrolled in fail-closed so a Redis outage cannot relax their brute-force
 * protection.
 *
 * <p>Two usage patterns are supported:
 * <ul>
 *   <li><b>Throughput limiting</b> ({@link #enforce}) — every call counts
 *       toward the limit. Used for order placement.</li>
 *   <li><b>Attempt limiting</b> ({@link #check} / {@link #penalize} /
 *       {@link #clear}) — only failures count, and a success clears the
 *       counter. Used for login, 2FA and password reset.</li>
 * </ul>
 *
 * <p>The in-memory fallback is per-application-instance and not shared across
 * a cluster; this is an accepted limitation for a single-instance deployment.
 */
@Service
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final Set<String> failClosedPrefixes;

    private static final String PREFIX = "ratelimit:";

    /** Hard cap on fallback map size, guarding against unbounded growth during a long outage. */
    private static final int MAX_MEMORY_ENTRIES = 10_000;

    /** In-memory fallback counters, keyed by the same key used in Redis. */
    private final ConcurrentHashMap<String, Counter> memoryStore = new ConcurrentHashMap<>();

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            @Value("${ratelimit.fail-closed-prefixes:login:,2fa:,password-reset:,lookup-email:}") List<String> failClosedPrefixes
    ) {
        this.redisTemplate = redisTemplate;
        this.failClosedPrefixes = failClosedPrefixes.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void logConfig() {
        log.info("RateLimiter fail-closed prefixes: {}", failClosedPrefixes);
    }

    // ───────────────────────────── Throughput limiting ─────────────────────────────

    /**
     * Records one hit against {@code key} and throws {@link RateLimitedException}
     * once the number of hits in the current window exceeds {@code maxHits}.
     */
    public void enforce(String key, int maxHits, Duration window) {
        long count = increment(key, window);
        if (count > maxHits) {
            throw new RateLimitedException(ttlSeconds(key, window));
        }
    }

    // ───────────────────────────── Attempt limiting ────────────────────────────────

    /** Throws {@link RateLimitedException} if {@code key} has already reached {@code maxAttempts}. */
    public void check(String key, int maxAttempts, Duration window) {
        if (currentCount(key, window) >= maxAttempts) {
            throw new RateLimitedException(ttlSeconds(key, window));
        }
    }

    /** Records one failed attempt against {@code key}, starting the window on the first failure. */
    public void penalize(String key, Duration window) {
        increment(key, window);
    }

    /** Clears the counter for {@code key} (e.g. after a successful login). */
    public void clear(String key) {
        String redisKey = PREFIX + key;
        try {
            redisTemplate.delete(redisKey);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable clearing rate-limit key '{}' — clearing in-memory fallback only", key);
        }
        memoryStore.remove(redisKey);
    }

    // ───────────────────────────── Internal counter ops ────────────────────────────

    private boolean isFailClosed(String key) {
        for (String prefix : failClosedPrefixes) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Increments the counter, sets the window TTL on first hit, and returns the new count. */
    private long increment(String key, Duration window) {
        String redisKey = PREFIX + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, window);
            }
            return count != null ? count : 1L;
        } catch (DataAccessException e) {
            if (isFailClosed(key)) {
                log.warn("Redis unavailable for fail-closed rate-limit key '{}' — denying request", key);
                throw new RateLimitedException(window.toSeconds());
            }
            log.warn("Redis unavailable for rate limiting — using in-memory fallback for '{}'", key);
            return incrementInMemory(redisKey, window);
        }
    }

    /** Reads the current count without incrementing. */
    private long currentCount(String key, Duration window) {
        String redisKey = PREFIX + key;
        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (DataAccessException e) {
            if (isFailClosed(key)) {
                log.warn("Redis unavailable for fail-closed rate-limit key '{}' — denying request", key);
                throw new RateLimitedException(window.toSeconds());
            }
            log.warn("Redis unavailable for rate limiting — using in-memory fallback for '{}'", key);
            Counter counter = memoryStore.get(redisKey);
            return (counter != null && !counter.isExpired()) ? counter.count : 0L;
        }
    }

    /** Seconds until the current window expires; used to populate Retry-After. */
    private long ttlSeconds(String key, Duration window) {
        String redisKey = PREFIX + key;
        try {
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : window.toSeconds();
        } catch (DataAccessException e) {
            Counter counter = memoryStore.get(redisKey);
            if (counter != null && !counter.isExpired()) {
                long remaining = (counter.windowStart + counter.windowMillis - System.currentTimeMillis()) / 1000;
                return Math.max(remaining, 1);
            }
            return window.toSeconds();
        }
    }

    private long incrementInMemory(String redisKey, Duration window) {
        if (memoryStore.size() > MAX_MEMORY_ENTRIES) {
            memoryStore.clear();
        }
        Counter counter = memoryStore.compute(redisKey, (k, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new Counter(System.currentTimeMillis(), window.toMillis());
            }
            existing.count++;
            return existing;
        });
        return counter.count;
    }

    /** Mutable in-memory counter for the Redis fallback path. */
    private static final class Counter {
        final long windowStart;
        final long windowMillis;
        int count;

        Counter(long windowStart, long windowMillis) {
            this.windowStart = windowStart;
            this.windowMillis = windowMillis;
            this.count = 1;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - windowStart > windowMillis;
        }
    }
}
