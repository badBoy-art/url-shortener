package com.urlshortener.common;

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

    /** 根据浏览器默认携带的 User-Agent 识别设备类型，返回三档标识：tablet / mobile / pc */
    public static String deviceLabel(String ua) {
        if (isTablet(ua)) {
            return WebUtil.LABEL_TABLET;
        }
        if (isAndroid(ua) || isIPhone(ua)) {
            return WebUtil.LABEL_MOBILE;
        }
        return WebUtil.LABEL_PC;
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
