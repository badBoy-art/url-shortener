package com.urlshortener.common;

import java.util.Locale;

/**
 * 打开方式（open_type）客户端判定，规则与 ohUrlShortener utils/useragent.go 保持一致。
 * 0 全部 1 微信 2 钉钉 3 iPhone 4 Android 5 iPad 6 Safari 7 Chrome 8 Firefox
 *
 * @deprecated 设备/浏览器识别已由 {@link com.urlshortener.service.UserAgentAnalysisService} 接管；
 *             仅保留 Client Hints 三段判断作为文档参考。
 */
@Deprecated
public final class UserAgentMatcher {

    private UserAgentMatcher() {
    }

    /**
     * 根据浏览器 Client Hints 请求头识别设备类型三档；
     * 信息不足无法判断时返回 {@link DeviceTier#UNKNOWN}。
     *
     * @deprecated 设备三档已由 {@link com.urlshortener.service.UserAgentAnalysisService#deviceTier(java.util.Map)} 接管，
     *             Yauaa 自动合并 Client Hints 与 User-Agent 一次解析。
     */
    @Deprecated
    public static DeviceTier deviceTierFromHints(String mobileHint, String platformHint, String modelHint) {
        String platform = unquote(trimToEmpty(platformHint).toLowerCase(Locale.ROOT));
        String model = unquote(trimToEmpty(modelHint)).toLowerCase(Locale.ROOT);
        String mobile = trimToEmpty(mobileHint);

        boolean isMobile = mobile.equals("?1") || mobile.equals("1") || mobile.equalsIgnoreCase("true");
        boolean isDesktop = mobile.equals("?0") || mobile.equals("0") || mobile.equalsIgnoreCase("false");

        switch (platform) {
            case "ipados" -> {
                return DeviceTier.TABLET;
            }
            case "ios" -> {
                // iPad 请求桌面模式时 Sec-CH-UA-Mobile 为 ?0
                if (isDesktop || model.contains("ipad")) {
                    return DeviceTier.TABLET;
                }
                return DeviceTier.MOBILE;
            }
            case "android" -> {
                // Android 平板与手机的 UA / Client Hints 均无法区分，平板应用请走 X-Platform 请求头
                return DeviceTier.MOBILE;
            }
            case "chrome os", "chromium os", "macos", "windows", "linux" -> {
                // iPadOS 13+ 桌面模式可能上报 macOS，结合型号再判断一次
                if (model.contains("ipad")) {
                    return DeviceTier.TABLET;
                }
                return DeviceTier.PC;
            }
            default -> {
            }
        }
        if (isMobile) {
            return DeviceTier.MOBILE;
        }
        if (isDesktop) {
            return DeviceTier.PC;
        }
        return DeviceTier.UNKNOWN;
    }

    private static String unquote(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '"') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '"') {
            end--;
        }
        return s.substring(start, end);
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}