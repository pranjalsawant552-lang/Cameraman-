package com.cameraman.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Caching is an optimisation, not a hard dependency.
 *
 * <p>A bare {@code CacheErrorHandler} @Bean is NOT picked up by Spring's cache interceptor: it only
 * uses the handler returned by a {@link CachingConfigurer}. Before this class existed the handler
 * in RedisConfig was dead code, so with Redis down every @Cacheable call (studio settings, packages,
 * dashboard...) threw "Unable to connect to Redis" up into the caller. That is what made the invoice
 * backfill fail for every booking: invoice creation reads the studio settings, which are cached.
 *
 * <p>With this in place a cache outage only logs a warning and the call falls through to the database.
 * Everything else (cacheManager, key generator) keeps Spring's defaults.
 */
@Slf4j
@Configuration
public class CacheResilienceConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET failed for '{}' key '{}': {}", cache.getName(), key, e.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed for '{}' key '{}': {}", cache.getName(), key, e.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT failed for '{}' key '{}': {}", cache.getName(), key, e.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR failed for '{}': {}", cache.getName(), e.getMessage());
            }
        };
    }
}
