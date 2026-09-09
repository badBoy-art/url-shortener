package com.urlshortener.common;

import jakarta.servlet.http.HttpServletRequest;

public final class WebUtil {

    /** 多目标短链选择请求头：值为创建短链时给每个 destUrl 指定的 label，匹配则跳转对应目标 */
    public static final String DEST_LABEL_HEADER = "X-Dest-Label";

    private WebUtil() {
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
