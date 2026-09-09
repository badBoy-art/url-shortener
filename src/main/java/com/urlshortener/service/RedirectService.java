package com.urlshortener.service;

import com.urlshortener.common.WebUtil;
import com.urlshortener.entity.AccessLog;
import com.urlshortener.entity.ShortLink;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortLinkService shortLinkService;
    private final AccessLogService accessLogService;
    private final ClickCountService clickCountService;

    public Optional<ShortLink> resolve(String code) {
        return shortLinkService.findOptionalByCode(code);
    }

    /** 记录访问日志（异步）+ 点击计数（Redis 原子自增、批量回写），均不影响主链路 */
    public void recordVisit(String code, HttpServletRequest request) {
        AccessLog log = new AccessLog();
        log.setShortCode(code);
        log.setIp(WebUtil.clientIp(request));
        log.setUserAgent(WebUtil.truncate(request.getHeader("User-Agent"), 512));
        log.setReferer(WebUtil.truncate(request.getHeader("Referer"), 2048));
        log.setVisitTime(LocalDateTime.now());
        accessLogService.offer(log);
        clickCountService.increment(code);
    }
}
