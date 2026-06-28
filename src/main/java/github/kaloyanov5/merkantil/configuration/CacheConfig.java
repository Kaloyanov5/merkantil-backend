package github.kaloyanov5.merkantil.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // GenericJackson2JsonRedisSerializer needs the mapper to embed type info,
        // otherwise cached records/POJOs round-trip as LinkedHashMap and the
        // @Cacheable proxy throws ClassCastException at the call site. EVERYTHING
        // (not NON_FINAL) is required because our DTOs are Java records (final).
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("github.kaloyanov5.merkantil")
                .allowIfSubType("java.util")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.math")
                .allowIfSubType("java.time")
                .build();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                "news", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                // marketStatus gates MARKET-order acceptance; the default 1-minute
                // TTL allowed up to ~60s of after-hours orders to slip through at
                // session boundaries. 5s keeps the upstream call rate manageable
                // while shrinking the boundary window to ~one snapshot tick.
                "marketStatus", defaultConfig.entryTtl(Duration.ofSeconds(5)),
                // Prices refresh every 5s via StockPriceScheduler with @CacheEvict.
                // TTL must not exceed that interval — otherwise a silently-swallowed
                // eviction failure (see LoggingCacheErrorHandler) would let the
                // cache serve up to one extra update cycle of stale prices.
                "stocks", defaultConfig.entryTtl(Duration.ofSeconds(5)),
                "stockSnapshots", defaultConfig.entryTtl(Duration.ofSeconds(5)),
                // On-demand portfolio analytics. 15-minute TTL keeps a new trade
                // reflected quickly without explicit eviction.
                "portfolioAnalytics", defaultConfig.entryTtl(Duration.ofMinutes(15))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Registers a {@link CacheErrorHandler} that swallows Redis failures, so a
     * Redis outage degrades the app to direct DB access instead of 500s.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
