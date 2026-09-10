package com.urlshortener.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 打开方式（open_type）UA 判定，规则与 ohUrlShortener utils/useragent.go 保持一致。
 * 0 全部 1 微信 2 钉钉 3 iPhone 4 Android 5 iPad 6 Safari 7 Chrome 8 Firefox
 */
public final class UserAgentMatcher {

    private static final Pattern ANDROID = Pattern.compile("(?i)Android/[\\d.]+");
    private static final Pattern IPHONE = Pattern.compile("(?i)iPhone/[\\d.]+");
    private static final Pattern IPAD = Pattern.compile("(?i)iPad/[\\d.]+");
    private static final Pattern TABLET = Pattern.compile("(?i)(iPad/[\\d.]+|Macintosh.*Mobile/[\\d.]+)");
    private static final Pattern WECHAT = Pattern.compile("(?i)MicroMessenger/[\\d.]+");
    private static final Pattern DINGTALK = Pattern.compile("(?i)DingTalk/[\\d.]+");
    private static final Pattern SAFARI = Pattern.compile("(?i)Version/[\\d.]+ Safari/[\\d.]+");
    private static final Pattern CHROME = Pattern.compile("(?i)Chrome/[\\d.]+ Safari");
    private static final Pattern FIREFOX = Pattern.compile("(?i)Firefox/[\\d.]+");

    private UserAgentMatcher() {
    }

    public static boolean isAndroid(String ua) {
        return matches(ANDROID, ua);
    }

    public static boolean isIPhone(String ua) {
        return matches(IPHONE, ua);
    }

    public static boolean isIPad(String ua) {
        return matches(IPAD, ua);
    }

    /** iPad 原生 UA，或 iPadOS 13+ 桌面模式 UA（Macintosh 平台 + Mobile 标记） */
    public static boolean isTablet(String ua) {
        return matches(TABLET, ua);
    }

    /**
     * 根据浏览器 Client Hints 请求头识别设备类型三档；
     * 信息不足无法判断时返回 {@link DeviceTier#UNKNOWN}，调用方应回退到 User-Agent 识别。
     */
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

    /** 根据 User-Agent 正则识别设备类型三档，识别不出时归为 PC */
    public static DeviceTier deviceTierFromUA(String ua) {
        if (isTablet(ua)) {
            return DeviceTier.TABLET;
        }
        if (isAndroid(ua) || isIPhone(ua)) {
            return DeviceTier.MOBILE;
        }
        return DeviceTier.PC;
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

    public static boolean isWeChat(String ua) {
        return matches(WECHAT, ua);
    }

    public static boolean isDingTalk(String ua) {
        return matches(DINGTALK, ua);
    }

    public static boolean isSafari(String ua) {
        return matches(SAFARI, ua);
    }

    public static boolean isChrome(String ua) {
        return matches(CHROME, ua);
    }

    public static boolean isFirefox(String ua) {
        return matches(FIREFOX, ua);
    }

    /** openType 为 0（全部）或未知值时放行 */
    public static boolean matches(int openType, String ua) {
        if (ua == null || ua.isEmpty()) {
            return false;
        }
        return switch (openType) {
            case 0 -> true;
            case 1 -> isWeChat(ua);
            case 2 -> isDingTalk(ua);
            case 3 -> isIPhone(ua);
            case 4 -> isAndroid(ua);
            case 5 -> isIPad(ua);
            case 6 -> isSafari(ua);
            case 7 -> isChrome(ua);
            case 8 -> isFirefox(ua);
            default -> true;
        };
    }

    private static boolean matches(Pattern pattern, String ua) {
        return ua != null && pattern.matcher(ua).find();
    }
}
