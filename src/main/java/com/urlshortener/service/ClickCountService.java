package com.urlshortener.service;

import com.urlshortener.config.RedisStateHolder;
import com.urlshortener.mapper.ShortLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 点击计数：请求内仅做 Redis INCR（O(1)），定时任务用 GETDEL 原子取回并批量回写 DB。
 * 多实例安全：GETDEL 保证同一计数只被一个实例取走，INCR/SADD 顺序保证不丢计数。
 * Redis 故障时降级为直写 DB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickCountService {

    private static final String DIRTY_KEY = "click:dirty";

    private final StringRedisTemplate redis;
    private final RedisStateHolder redisState;
    private final ShortLinkMapper mapper;

    public void increment(String code) {
        if (!redisState.isUp()) {
            incrementDb(code, 1);
            return;
        }
        try {
            redis.opsForValue().increment("click:" + code);
            redis.opsForSet().add(DIRTY_KEY, code);
        } catch (Exception e) {
            redisState.markDown(e);
            incrementDb(code, 1);
        }
    }

    @Scheduled(fixedDelayString = "${app.count-flush-interval-ms:30000}",
            initialDelayString = "${app.count-flush-interval-ms:30000}")
    public void flushDirtyCounters() {
        if (!redisState.isUp()) {
            return;
        }
        try {
            Set<String> codes = redis.opsForSet().members(DIRTY_KEY);
            for (String code : codes) {
                String deltaStr = redis.opsForValue().getAndDelete("click:" + code);
                long delta = deltaStr == null ? 0 : Long.parseLong(deltaStr);
                if (delta > 0) {
                    incrementDb(code, delta);
                }
                redis.opsForSet().remove(DIRTY_KEY, code);
            }
        } catch (Exception e) {
            redisState.markDown(e);
        }
    }

    private void incrementDb(String code, long delta) {
        try {
            mapper.incrementClick(code, delta);
        } catch (Exception e) {
            log.warn("回写点击计数失败: code={}, delta={}", code, delta, e);
        }
    }
}
