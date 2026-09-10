package com.urlshortener.service;

import com.urlshortener.common.DeviceTier;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Yauaa User-Agent + Client Hints 解析服务。
 * 构造昂贵但线程安全，作为 Spring 单例 Bean 使用。
 * 传入全部 HTTP 请求头（含 User-Agent + Sec-CH-UA-*），Yauaa 自动合并推断。
 */
@Service
public class UserAgentAnalysisService {

    private final UserAgentAnalyzer analyzer;

    public UserAgentAnalysisService() {
        this.analyzer = UserAgentAnalyzer.newBuilder()
                .withCache(10_000)
                .hideMatcherLoadStats()
                .build();
    }

    /** 传入全部 HTTP 请求头，返回设备三档（PC / MOBILE / TABLET） */
    public DeviceTier deviceTier(Map<String, String> headers) {
        UserAgent ua = analyzer.parse(headers);
        String deviceClass = ua.getValue(UserAgent.DEVICE_CLASS);
        return switch (deviceClass) {
            case "Phone", "Smartphone", "Mobile" -> DeviceTier.MOBILE;
            case "Tablet", "iPad", "Ereader" -> DeviceTier.TABLET;
            case "Desktop", "Laptop", "Server" -> DeviceTier.PC;
            default -> DeviceTier.PC;
        };
    }

    /** 是否匹配指定 open_type（与原 UserAgentMatcher.matches() 逻辑一致） */
    public boolean matchesOpenType(int openType, Map<String, String> headers) {
        if (openType == 0) {
            return true;
        }
        UserAgent ua = analyzer.parse(headers);
        return switch (openType) {
            case 1 -> "WeChat".equals(ua.getValue(UserAgent.AGENT_NAME));
            case 2 -> "DingTalk".equals(ua.getValue(UserAgent.AGENT_NAME))
                    || (headers.get("User-Agent") != null && headers.get("User-Agent").contains("DingTalk"));
            case 3 -> "Phone".equals(ua.getValue(UserAgent.DEVICE_CLASS))
                    && "iOS".equals(ua.getValue(UserAgent.OPERATING_SYSTEM_NAME));
            case 4 -> "Android".equals(ua.getValue(UserAgent.OPERATING_SYSTEM_NAME));
            case 5 -> "Tablet".equals(ua.getValue(UserAgent.DEVICE_CLASS));
            case 6 -> "Safari".equals(ua.getValue(UserAgent.AGENT_NAME));
            case 7 -> "Chrome".equals(ua.getValue(UserAgent.AGENT_NAME));
            case 8 -> "Firefox".equals(ua.getValue(UserAgent.AGENT_NAME));
            default -> true;
        };
    }
}