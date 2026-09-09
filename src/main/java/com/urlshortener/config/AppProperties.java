package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String apiKey,
        ShortCode shortCode,
        Cache cache,
        RateLimit rateLimit,
        AccessLog accessLog,
        Auth auth,
        Admin admin,
        long countFlushIntervalMs
) {

    public record ShortCode(int length, int maxRetries) {
    }

    public record Cache(int localTtlMinutes, long localMaxSize, int redisTtlHours) {
    }

    public record RateLimit(int limit, int windowSeconds) {
    }

    public record AccessLog(long flushIntervalMs, int batchSize, int queueCapacity) {
    }

    public record Auth(String jwtSecret, int jwtExpireHours, int loginMaxFails, int loginLockMinutes) {
    }

    public record Admin(String initAccount, String initPassword, int exportMaxRows) {
    }
}
