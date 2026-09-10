package com.urlshortener.codec;

import com.urlshortener.common.UserAgentMatcher;
import com.urlshortener.common.WebUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentMatcherTest {

    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String WECHAT_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49";
    private static final String WECHAT_ANDROID_UA = "Mozilla/5.0 (Linux; Android/13; SM-G9910) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Mobile Safari/537.36 MicroMessenger/8.0.40";
    private static final String DINGTALK_UA = "Mozilla/5.0 (Linux; U; Android 10; zh-CN) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Version/4.0 Chrome/77.0.3865.120 MQQBrowser/11.9 Mobile Safari/537.36 "
            + "COVC/046709 UA/1.1 DingTalk/6.5.50";
    private static final String SAFARI_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.4 Safari/605.1.15";
    private static final String FIREFOX_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0";
    private static final String ANDROID_UA = "Mozilla/5.0 (Linux; U; Android/14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String IPHONE_UA = "Mozilla/5.0 (iPhone/17.5; CPU iPhone OS 17_5 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
    private static final String IPAD_UA = "Mozilla/5.0 (iPad/17.5; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
    private static final String IPADOS_DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/13.0 Safari/605.1.15 Mobile/15E148";

    @Test
    void detectsEachClient() {
        assertThat(UserAgentMatcher.isAndroid(ANDROID_UA)).isTrue();
        assertThat(UserAgentMatcher.isAndroid(CHROME_UA)).isFalse();
        assertThat(UserAgentMatcher.isIPhone(IPHONE_UA)).isTrue();
        assertThat(UserAgentMatcher.isIPhone(IPAD_UA)).isFalse();
        assertThat(UserAgentMatcher.isIPad(IPAD_UA)).isTrue();
        assertThat(UserAgentMatcher.isWeChat(WECHAT_UA)).isTrue();
        assertThat(UserAgentMatcher.isWeChat(CHROME_UA)).isFalse();
        assertThat(UserAgentMatcher.isDingTalk(DINGTALK_UA)).isTrue();
        assertThat(UserAgentMatcher.isDingTalk(CHROME_UA)).isFalse();
        assertThat(UserAgentMatcher.isSafari(SAFARI_UA)).isTrue();
        assertThat(UserAgentMatcher.isSafari(CHROME_UA)).isFalse();
        assertThat(UserAgentMatcher.isChrome(CHROME_UA)).isTrue();
        assertThat(UserAgentMatcher.isChrome(FIREFOX_UA)).isFalse();
        assertThat(UserAgentMatcher.isFirefox(FIREFOX_UA)).isTrue();
        assertThat(UserAgentMatcher.isFirefox(CHROME_UA)).isFalse();
    }

    @Test
    void detectsTablet() {
        assertThat(UserAgentMatcher.isTablet(IPAD_UA)).isTrue();
        assertThat(UserAgentMatcher.isTablet(IPADOS_DESKTOP_UA)).isTrue();
        assertThat(UserAgentMatcher.isTablet(ANDROID_UA)).isFalse();
        assertThat(UserAgentMatcher.isTablet(IPHONE_UA)).isFalse();
        assertThat(UserAgentMatcher.isTablet(CHROME_UA)).isFalse();
    }

    @Test
    void deviceLabelThreeTiers() {
        assertThat(UserAgentMatcher.deviceLabel(CHROME_UA)).isEqualTo(WebUtil.LABEL_PC);
        assertThat(UserAgentMatcher.deviceLabel(SAFARI_UA)).isEqualTo(WebUtil.LABEL_PC);
        assertThat(UserAgentMatcher.deviceLabel(ANDROID_UA)).isEqualTo(WebUtil.LABEL_MOBILE);
        assertThat(UserAgentMatcher.deviceLabel(IPHONE_UA)).isEqualTo(WebUtil.LABEL_MOBILE);
        assertThat(UserAgentMatcher.deviceLabel(WECHAT_ANDROID_UA)).isEqualTo(WebUtil.LABEL_MOBILE);
        assertThat(UserAgentMatcher.deviceLabel(IPAD_UA)).isEqualTo(WebUtil.LABEL_TABLET);
        assertThat(UserAgentMatcher.deviceLabel(IPADOS_DESKTOP_UA)).isEqualTo(WebUtil.LABEL_TABLET);
        assertThat(UserAgentMatcher.deviceLabel("")).isEqualTo(WebUtil.LABEL_PC);
        assertThat(UserAgentMatcher.deviceLabel(null)).isEqualTo(WebUtil.LABEL_PC);
    }

    @Test
    void openTypeAllAlwaysMatches() {
        assertThat(UserAgentMatcher.matches(0, CHROME_UA)).isTrue();
        assertThat(UserAgentMatcher.matches(0, null)).isFalse();
        assertThat(UserAgentMatcher.matches(0, "")).isFalse();
    }

    @Test
    void openTypeSwitchBehavior() {
        assertThat(UserAgentMatcher.matches(1, WECHAT_UA)).isTrue();
        assertThat(UserAgentMatcher.matches(1, CHROME_UA)).isFalse();
        assertThat(UserAgentMatcher.matches(7, CHROME_UA)).isTrue();
        assertThat(UserAgentMatcher.matches(7, FIREFOX_UA)).isFalse();
        // 未知 openType 值放行（容错）
        assertThat(UserAgentMatcher.matches(99, CHROME_UA)).isTrue();
        // null UA 一律拒绝
        assertThat(UserAgentMatcher.matches(7, null)).isFalse();
    }
}
