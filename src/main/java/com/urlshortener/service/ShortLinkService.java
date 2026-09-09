package com.urlshortener.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.urlshortener.cache.LinkCacheService;
import com.urlshortener.cache.LinkInfo;
import com.urlshortener.common.Base58;
import com.urlshortener.common.BusinessException;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.RedisStateHolder;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.DestInfo;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.entity.ShortLink;
import com.urlshortener.entity.ShortLinkDest;
import com.urlshortener.mapper.ShortLinkDestMapper;
import com.urlshortener.mapper.ShortLinkMapper;
import com.urlshortener.service.codec.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkService {

    private final ShortLinkMapper mapper;
    private final ShortLinkDestMapper destMapper;
    private final ShortCodeGenerator codeGenerator;
    private final LinkCacheService cacheService;
    private final AppProperties props;
    private final StringRedisTemplate redis;
    private final RedisStateHolder redisState;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public LinkResponse create(CreateLinkRequest request) {
        String destUrl = resolvePrimaryDestUrl(request);
        if (request.expiredAt() != null && request.expiredAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.badRequest("过期时间不能早于当前时间");
        }
        if (request.shortCode() != null && !request.shortCode().isBlank()) {
            return createWithCustomCode(request, destUrl);
        }
        return createWithGeneratedCode(request, destUrl);
    }

    /** 主目标地址：destUrl 字段优先，否则取 destinations 中第一个；用于短码生成与无 X-Dest-Label 请求头时的默认跳转 */
    private String resolvePrimaryDestUrl(CreateLinkRequest request) {
        boolean hasDestUrl = request.destUrl() != null && !request.destUrl().isBlank();
        List<DestInfo> dests = request.destinations() == null ? List.of() : request.destinations();
        if (!hasDestUrl && dests.isEmpty()) {
            throw BusinessException.badRequest("destUrl 与 destinations 至少提供一个");
        }
        String primary = hasDestUrl ? request.destUrl().trim() : dests.get(0).destUrl().trim();
        validateUrl(primary);
        return primary;
    }

    /** 校验并归一化多目标列表（label → destUrl），label 去空格且不允许重复 */
    private Map<String, String> destinationMap(CreateLinkRequest request) {
        List<DestInfo> dests = request.destinations() == null ? List.of() : request.destinations();
        Map<String, String> map = new LinkedHashMap<>();
        for (DestInfo dest : dests) {
            String label = dest.label().trim();
            String url = dest.destUrl().trim();
            validateUrl(url);
            if (map.putIfAbsent(label, url) != null) {
                throw BusinessException.badRequest("destinations 中 label 重复: " + label);
            }
        }
        return map;
    }

    private LinkResponse createWithGeneratedCode(CreateLinkRequest request, String destUrl) {
        String urlKey = codeGenerator.urlCacheKey(destUrl);
        ShortLink existing = findByCodeFromUrlCache(urlKey);
        if (existing != null && existing.getDestUrl().equals(destUrl)) {
            return LinkResponse.from(existing, props.baseUrl());
        }
        for (int attempt = 0; attempt <= codeGenerator.maxRetries(); attempt++) {
            String code = codeGenerator.generate(destUrl, attempt);
            ShortLink byCode = findLinkByCode(code);
            if (byCode != null) {
                if (byCode.getDestUrl().equals(destUrl)) {
                    cacheUrlCode(urlKey, code);
                    return LinkResponse.from(byCode, props.baseUrl());
                }
                // 真冲突：该码被其它 URL 占用，换盐重试
                continue;
            }
            try {
                ShortLink link = insert(request, destUrl, code);
                cacheUrlCode(urlKey, code);
                return LinkResponse.from(link, props.baseUrl());
            } catch (DuplicateKeyException e) {
                // 并发下同 URL 抢先插入：幂等返回已有结果
                ShortLink winner = findLinkByCode(code);
                if (winner != null && winner.getDestUrl().equals(destUrl)) {
                    cacheUrlCode(urlKey, code);
                    return LinkResponse.from(winner, props.baseUrl());
                }
            }
        }
        throw BusinessException.internal("短码生成冲突次数超限，请重试");
    }

    private LinkResponse createWithCustomCode(CreateLinkRequest request, String destUrl) {
        String code = request.shortCode().trim();
        if (!Base58.isValidCode(code)) {
            throw BusinessException.badRequest("短码只能为 4-16 位 Base58 字符（不含 0、O、I、l）");
        }
        String urlKey = codeGenerator.urlCacheKey(destUrl);
        ShortLink byCode = findLinkByCode(code);
        if (byCode != null) {
            if (byCode.getDestUrl().equals(destUrl)) {
                cacheUrlCode(urlKey, code);
                return LinkResponse.from(byCode, props.baseUrl());
            }
            throw BusinessException.conflict("短码 " + code + " 已被占用");
        }
        try {
            ShortLink link = insert(request, destUrl, code);
            cacheUrlCode(urlKey, code);
            return LinkResponse.from(link, props.baseUrl());
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("短码 " + code + " 已被占用");
        }
    }

    private ShortLink insert(CreateLinkRequest request, String destUrl, String code) {
        ShortLink link = new ShortLink();
        link.setShortCode(code);
        link.setDestUrl(destUrl);
        if (request.password() != null && !request.password().isBlank()) {
            link.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        link.setDescription(request.description());
        link.setExpiredAt(request.expiredAt());
        link.setStatus(1);
        link.setOpenType(request.openType() == null ? 0 : request.openType());
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        Map<String, String> dests = destinationMap(request);
        link.setDestinations(dests);
        mapper.insert(link);
        for (Map.Entry<String, String> entry : dests.entrySet()) {
            ShortLinkDest dest = new ShortLinkDest();
            dest.setShortCode(code);
            dest.setLabel(entry.getKey());
            dest.setDestUrl(entry.getValue());
            dest.setCreatedAt(LocalDateTime.now());
            destMapper.insert(dest);
        }
        putCacheAfterCommit(link);
        return link;
    }

    /** 事务提交后再写缓存，避免回滚导致缓存脏数据 */
    private void putCacheAfterCommit(ShortLink link) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.put(LinkInfo.from(link));
                }
            });
        } else {
            cacheService.put(LinkInfo.from(link));
        }
    }

    public Optional<ShortLink> findOptionalByCode(String code) {
        return cacheService.get(code, () -> {
            ShortLink link = mapper.selectOne(
                    Wrappers.<ShortLink>lambdaQuery().eq(ShortLink::getShortCode, code));
            if (link == null) {
                return null;
            }
            link.setDestinations(loadDestinations(code));
            return LinkInfo.from(link);
        }).map(LinkInfo::toEntity);
    }

    private Map<String, String> loadDestinations(String code) {
        List<ShortLinkDest> dests = destMapper.selectList(
                Wrappers.<ShortLinkDest>lambdaQuery().eq(ShortLinkDest::getShortCode, code));
        Map<String, String> map = new LinkedHashMap<>();
        for (ShortLinkDest dest : dests) {
            map.put(dest.getLabel(), dest.getDestUrl());
        }
        return map;
    }

    public ShortLink getOrThrow(String code) {
        return findOptionalByCode(code)
                .orElseThrow(() -> BusinessException.notFound("短链 " + code + " 不存在"));
    }

    public LinkResponse getInfo(String code) {
        return getInfo(code, null);
    }

    /** 查询短链信息：label 命中多目标则 destUrl 返回对应目标，否则返回主目标（兼容旧短链） */
    public LinkResponse getInfo(String code, String label) {
        ShortLink link = getOrThrow(code);
        return LinkResponse.from(link, props.baseUrl(), resolveDestUrl(link, label));
    }

    /** 按 X-Dest-Label 请求头解析目标地址：匹配 label 返回对应目标，无匹配/无请求头返回主目标 */
    public String resolveDestUrl(ShortLink link, String label) {
        if (label != null && !label.isBlank() && link.getDestinations() != null) {
            String target = link.getDestinations().get(label.trim());
            if (target != null) {
                return target;
            }
        }
        return link.getDestUrl();
    }

    public void delete(String code) {
        ShortLink link = getOrThrow(code);
        mapper.delete(Wrappers.<ShortLink>lambdaQuery().eq(ShortLink::getShortCode, code));
        destMapper.delete(Wrappers.<ShortLinkDest>lambdaQuery().eq(ShortLinkDest::getShortCode, code));
        cacheService.evict(code);
        evictUrlCode(link.getDestUrl());
    }

    private ShortLink findLinkByCode(String code) {
        return findOptionalByCode(code).orElse(null);
    }

    private ShortLink findByCodeFromUrlCache(String urlKey) {
        if (!redisState.isUp()) {
            return null;
        }
        try {
            String code = redis.opsForValue().get("url:code:" + urlKey);
            if (code != null) {
                return findLinkByCode(code);
            }
        } catch (Exception e) {
            redisState.markDown(e);
        }
        return null;
    }

    private void cacheUrlCode(String urlKey, String code) {
        if (!redisState.isUp()) {
            return;
        }
        try {
            redis.opsForValue().set("url:code:" + urlKey, code, Duration.ofHours(props.cache().redisTtlHours()));
        } catch (Exception e) {
            redisState.markDown(e);
        }
    }

    private void evictUrlCode(String destUrl) {
        if (!redisState.isUp()) {
            return;
        }
        try {
            redis.delete("url:code:" + codeGenerator.urlCacheKey(destUrl));
        } catch (Exception e) {
            redisState.markDown(e);
        }
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw BusinessException.badRequest("仅支持 http/https 协议的有效地址");
            }
        } catch (URISyntaxException e) {
            throw BusinessException.badRequest("目标地址格式非法");
        }
    }
}
