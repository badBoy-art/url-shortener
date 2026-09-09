package com.urlshortener.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.RedisStateHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 三级读缓存：Caffeine 本地（热点直读内存）→ Redis（实例共享）→ MySQL。
 * Redis 故障时自动降级为 本地 + MySQL，重定向不受影响。
 * 未命中的短码做 1 分钟本地负缓存，防止随机码探测打穿 DB。
 * 删除/停用等变更通过 Redis pub/sub 广播失效，多实例本地缓存毫秒级一致。
 */
@Slf4j
@Component
public class LinkCacheService {

    private static final long NEGATIVE_TTL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final String EVICT_CHANNEL = "link:evict";

    private final Cache<String, Optional<LinkInfo>> local;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisStateHolder redisState;
    private final AppProperties props;
    private RedisMessageListenerContainer evictListener;

    public LinkCacheService(StringRedisTemplate redis, ObjectMapper objectMapper,
                            RedisStateHolder redisState, AppProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.redisState = redisState;
        this.props = props;
        long localTtlNanos = TimeUnit.MINUTES.toNanos(props.cache().localTtlMinutes());
        this.local = Caffeine.newBuilder()
                .maximumSize(props.cache().localMaxSize())
                .expireAfter(new Expiry<String, Optional<LinkInfo>>() {
                    @Override
                    public long expireAfterCreate(String key, Optional<LinkInfo> value, long currentTime) {
                        return value.isPresent() ? localTtlNanos : NEGATIVE_TTL_NANOS;
                    }

                    @Override
                    public long expireAfterUpdate(String key, Optional<LinkInfo> value,
                                                  long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, Optional<LinkInfo> value,
                                                long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    public Optional<LinkInfo> get(String code, Supplier<LinkInfo> dbLoader) {
        Optional<LinkInfo> cached = local.getIfPresent(code);
        if (cached != null) {
            return cached;
        }
        if (redisState.isUp()) {
            try {
                String json = redis.opsForValue().get(redisKey(code));
                if (json != null) {
                    LinkInfo info = objectMapper.readValue(json, LinkInfo.class);
                    local.put(code, Optional.of(info));
                    return Optional.of(info);
                }
            } catch (Exception e) {
                redisState.markDown(e);
            }
        }
        LinkInfo info = dbLoader.get();
        local.put(code, Optional.ofNullable(info));
        if (info != null && redisState.isUp()) {
            writeRedis(code, info);
        }
        return Optional.ofNullable(info);
    }

    public void put(LinkInfo info) {
        local.put(info.code(), Optional.of(info));
        if (redisState.isUp()) {
            writeRedis(info.code(), info);
        }
    }

    public void evict(String code) {
        local.invalidate(code);
        if (redisState.isUp()) {
            try {
                redis.delete(redisKey(code));
                // 广播失效，其它实例同步剔除本地缓存；Redis 故障时退化为仅本实例失效
                redis.convertAndSend(EVICT_CHANNEL, code);
            } catch (Exception e) {
                redisState.markDown(e);
            }
        }
    }

    @PostConstruct
    void startEvictListener() {
        RedisConnectionFactory factory = redis.getConnectionFactory();
        if (factory == null) {
            log.warn("Redis 连接工厂不可用，跨实例缓存失效广播未启用");
            return;
        }
        try {
            evictListener = new RedisMessageListenerContainer();
            evictListener.setConnectionFactory(factory);
            evictListener.addMessageListener((message, pattern) -> {
                local.invalidate(new String(message.getBody(), StandardCharsets.UTF_8));
            }, new ChannelTopic(EVICT_CHANNEL));
            evictListener.afterPropertiesSet();
            evictListener.start();
        } catch (Exception e) {
            // fail-open：Redis 启动时不可用不应阻断应用；恢复后读路径自动降级，仅跨实例失效广播缺失
            evictListener = null;
            log.warn("跨实例缓存失效广播启动失败，退化为仅本实例失效", e);
        }
    }

    @PreDestroy
    void stopEvictListener() {
        if (evictListener != null) {
            evictListener.stop();
        }
    }

    private void writeRedis(String code, LinkInfo info) {
        try {
            redis.opsForValue().set(redisKey(code), objectMapper.writeValueAsString(info),
                    Duration.ofHours(props.cache().redisTtlHours()));
        } catch (Exception e) {
            redisState.markDown(e);
        }
    }

    private String redisKey(String code) {
        return "link:" + code;
    }
}
