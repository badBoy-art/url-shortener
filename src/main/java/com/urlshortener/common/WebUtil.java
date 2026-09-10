package com.urlshortener.common;

import com.urlshortener.service.UserAgentAnalysisService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WebUtil {

    /** 客户端类型请求头：由 App 等客户端自行设置（如 app、wechat），浏览器不会携带 */
    public static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    /** 客户端平台请求头：由 App 等客户端自行设置（如 android、ios、ipad），浏览器不会携带 */
    public static final String PLATFORM_HEADER = "X-Platform";

    /** Accept-CH 响应头：声明本服务需要的 Client Hints，浏览器收到后在后续请求中携带 Sec-CH-UA-* */
    public static final String ACCEPT_CH_HEADER = "Accept-CH";
    public static final String ACCEPT_CH_VALUE = "Sec-CH-UA-Mobile, Sec-CH-UA-Platform, Sec-CH-UA-Model";

    /** 无上述请求头（如浏览器访问）时，根据 User-Agent 自动识别设备类型所用的标识 */
    public static final String LABEL_PC = "pc";
    public static final String LABEL_MOBILE = "mobile";
    public static final String LABEL_TABLET = "tablet";

    private WebUtil() {
    }

    /**
     * 组装目标地址标识的匹配优先级：
     * X-Client-Type > X-Platform > Yauaa（Client Hints + User-Agent 合并推断，pc/mobile/tablet）
     */
    public static List<String> destLabels(HttpServletRequest request, UserAgentAnalysisService uaService) {
        List<String> labels = new ArrayList<>(3);
        appendHeader(labels, request, CLIENT_TYPE_HEADER);
        appendHeader(labels, request, PLATFORM_HEADER);
        labels.add(deviceLabel(request, uaService));
        return labels;
    }

    /** 收集全部请求头为 Map，传给 Yauaa 一次性解析 User-Agent + Client Hints 后返回设备三档标识 */
    private static String deviceLabel(HttpServletRequest request, UserAgentAnalysisService uaService) {
        return switch (uaService.deviceTier(headerMap(request))) {
            case TABLET -> LABEL_TABLET;
            case MOBILE -> LABEL_MOBILE;
            default -> LABEL_PC;
        };
    }

    private static Map<String, String> headerMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> map.put(name, request.getHeader(name)));
        return map;
    }

    private static void appendHeader(List<String> labels, HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            labels.add(value);
        }
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return truncate(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    public static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}