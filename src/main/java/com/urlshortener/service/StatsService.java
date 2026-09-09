package com.urlshortener.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.urlshortener.common.BusinessException;
import com.urlshortener.dto.OverviewResponse;
import com.urlshortener.dto.StatsResponse;
import com.urlshortener.entity.ShortLink;
import com.urlshortener.mapper.AccessLogMapper;
import com.urlshortener.mapper.ShortLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ShortLinkMapper linkMapper;
    private final AccessLogMapper logMapper;

    public StatsResponse linkStats(String code) {
        // 直读 DB，避免缓存中 clickCount 过期
        ShortLink link = linkMapper.selectOne(
                Wrappers.<ShortLink>lambdaQuery().eq(ShortLink::getShortCode, code));
        if (link == null) {
            throw BusinessException.notFound("短链 " + code + " 不存在");
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime sevenDaysStart = todayStart.minusDays(6);
        LocalDateTime monthStart = todayStart.withDayOfMonth(1);
        List<StatsResponse.HourlyStat> hourly = logMapper.hourlyPv(code, todayStart).stream()
                .map(h -> new StatsResponse.HourlyStat(h.getHour(), h.getPv() == null ? 0 : h.getPv()))
                .toList();
        return new StatsResponse(
                code,
                link.getClickCount() == null ? 0 : link.getClickCount(),
                logMapper.countSince(code, todayStart),
                logMapper.countDistinctIpSince(code, todayStart),
                logMapper.countBetween(code, yesterdayStart, todayStart),
                logMapper.countDistinctIpBetween(code, yesterdayStart, todayStart),
                logMapper.countSince(code, sevenDaysStart),
                logMapper.countDistinctIpSince(code, sevenDaysStart),
                logMapper.countSince(code, monthStart),
                logMapper.countDistinctIpSince(code, monthStart),
                hourly
        );
    }

    public OverviewResponse overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime sevenDaysStart = todayStart.minusDays(6);
        LocalDateTime monthStart = todayStart.withDayOfMonth(1);
        List<OverviewResponse.TopLink> top = linkMapper.topByClicks(10).stream()
                .map(l -> new OverviewResponse.TopLink(
                        l.getShortCode(), l.getDestUrl(),
                        l.getClickCount() == null ? 0 : l.getClickCount()))
                .toList();
        return new OverviewResponse(
                linkMapper.countAll(),
                linkMapper.sumClicks(),
                logMapper.countAllSince(todayStart),
                logMapper.countDistinctIpAllSince(todayStart),
                logMapper.countAllBetween(yesterdayStart, todayStart),
                logMapper.countDistinctIpAllBetween(yesterdayStart, todayStart),
                logMapper.countAllSince(sevenDaysStart),
                logMapper.countDistinctIpAllSince(sevenDaysStart),
                logMapper.countAllSince(monthStart),
                logMapper.countDistinctIpAllSince(monthStart),
                top
        );
    }
}
