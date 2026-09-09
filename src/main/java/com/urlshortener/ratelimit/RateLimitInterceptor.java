package com.urlshortener.ratelimit;

import com.urlshortener.common.BusinessException;
import com.urlshortener.common.WebUtil;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.RedisStateHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis ZSET 滑动窗口限流。Redis 故障时 fail-open（可用性优先）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final DefaultRedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return -1
            end
            redis.call('ZADD', key, now, ARGV[4])
            redis.call('PEXPIRE', key, window)
            return limit - count - 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RedisStateHolder redisState;
    private final AppProperties props;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !handlerMethod.hasMethodAnnotation(RateLimit.class)) {
            return true;
        }
        if (!redisState.isUp()) {
            return true;
        }
        String key = "rl:" + WebUtil.clientIp(request) + ":" + handlerMethod.getMethod().getName();
        try {
            long now = System.currentTimeMillis();
            long windowMs = props.rateLimit().windowSeconds() * 1000L;
            Long remaining = redis.execute(SLIDING_WINDOW, List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(props.rateLimit().limit()),
                    now + ":" + ThreadLocalRandom.current().nextLong());
            if (remaining != null && remaining < 0) {
                response.setHeader("Retry-After", String.valueOf(props.rateLimit().windowSeconds()));
                throw BusinessException.tooManyRequests("请求过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            redisState.markDown(e);
        }
        return true;
    }
}
