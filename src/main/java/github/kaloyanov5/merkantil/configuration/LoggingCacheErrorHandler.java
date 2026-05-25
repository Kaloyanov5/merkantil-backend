package github.kaloyanov5.merkantil.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Swallows cache-backend (Redis) failures so a Redis outage degrades the
 * application to direct database access instead of producing 500s.
 *
 * <p>Without this handler a failed {@code @CacheEvict} / {@code @Cacheable}
 * propagates out of the caching proxy and breaks its caller — including the
 * scheduled price update, whose {@code @CacheEvict} fires every 30 seconds and
 * whose failure escapes the method's own try/catch.
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache GET failed [{}], serving from source: {}", cache.getName(), exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache PUT failed [{}], skipping cache write: {}", cache.getName(), exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache EVICT failed [{}], skipping eviction: {}", cache.getName(), exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache CLEAR failed [{}], skipping clear: {}", cache.getName(), exception.getMessage());
    }
}
