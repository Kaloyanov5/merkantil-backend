package github.kaloyanov5.merkantil.common.ratelimit;

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
            @Value("${ratelimit.fail-closed-prefixes:login:,2fa:,reset:,lookup:}") List<String> failClosedPrefixes
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

    public void enforce(String key, int maxHits, Duration window) {
        long count = increment(key, window);
        if (count > maxHits) {
            throw new RateLimitedException(ttlSeconds(key, window));
        }
    }

    public void check(String key, int maxAttempts, Duration window) {
        if (currentCount(key, window) >= maxAttempts) {
            throw new RateLimitedException(ttlSeconds(key, window));
        }
    }

    public void penalize(String key, Duration window) {
        increment(key, window);
    }

    public void clear(String key) {
        String redisKey = PREFIX + key;
        try {
            redisTemplate.delete(redisKey);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable clearing rate-limit key '{}' — clearing in-memory fallback only", key);
        }
        memoryStore.remove(redisKey);
    }

    private boolean isFailClosed(String key) {
        for (String prefix : failClosedPrefixes) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

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
