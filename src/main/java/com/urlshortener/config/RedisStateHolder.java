package com.urlshortener.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 可用性状态：任何缓存操作失败即标记降级（fail-open，直查 MySQL），
 * 后台每 30s 探测一次，恢复后自动切回缓存路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStateHolder {

    private final StringRedisTemplate redisTemplate;
    private volatile boolean up = true;

    public boolean isUp() {
        return up;
    }

    public void markDown(Throwable cause) {
        up = false;
        log.warn("Redis unavailable, degraded to MySQL-only mode: {}", cause.getMessage());
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tryRecover() {
        if (up) {
            return;
        }
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            up = true;
            log.info("Redis recovered, cache path restored");
        } catch (Exception ignored) {
            // still down, retry next round
        }
    }
}
