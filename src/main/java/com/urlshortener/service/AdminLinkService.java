package com.urlshortener.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.urlshortener.cache.LinkCacheService;
import com.urlshortener.common.BusinessException;
import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.entity.ShortLink;
import com.urlshortener.entity.ShortLinkDest;
import com.urlshortener.mapper.ShortLinkDestMapper;
import com.urlshortener.mapper.ShortLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminLinkService {

    private final ShortLinkMapper mapper;
    private final ShortLinkDestMapper destMapper;
    private final LinkCacheService cacheService;
    private final AppProperties props;

    /** 分页 + keyword 搜索（短码/目标地址/备注 模糊匹配） */
    public Page<ShortLink> listLinks(String keyword, int page, int size) {
        LambdaQueryWrapper<ShortLink> wrapper = Wrappers.<ShortLink>lambdaQuery()
                .orderByDesc(ShortLink::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ShortLink::getShortCode, kw)
                    .or().like(ShortLink::getDestUrl, kw)
                    .or().like(ShortLink::getDescription, kw));
        }
        Page<ShortLink> result = mapper.selectPage(new Page<>(page, size), wrapper);
        attachDestinations(result.getRecords());
        return result;
    }

    /** 为分页查询出的短链批量附加多目标地址（管理端展示用） */
    private void attachDestinations(List<ShortLink> links) {
        if (links.isEmpty()) {
            return;
        }
        List<String> codes = links.stream().map(ShortLink::getShortCode).toList();
        List<ShortLinkDest> dests = destMapper.selectList(
                Wrappers.<ShortLinkDest>lambdaQuery().in(ShortLinkDest::getShortCode, codes));
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        for (ShortLinkDest dest : dests) {
            grouped.computeIfAbsent(dest.getShortCode(), k -> new LinkedHashMap<>())
                    .put(dest.getLabel(), dest.getDestUrl());
        }
        for (ShortLink link : links) {
            link.setDestinations(grouped.get(link.getShortCode()));
        }
    }

    /** 启用/停用；直查 DB（管理面低频，不走缓存），改完清缓存 */
    public LinkResponse setStatus(String code, boolean enable) {
        ShortLink link = mapper.selectOne(
                Wrappers.<ShortLink>lambdaQuery().eq(ShortLink::getShortCode, code));
        if (link == null) {
            throw BusinessException.notFound("短链 " + code + " 不存在");
        }
        link.setStatus(enable ? 1 : 0);
        mapper.updateById(link);
        cacheService.evict(code);
        return LinkResponse.from(link, props.baseUrl());
    }
}
