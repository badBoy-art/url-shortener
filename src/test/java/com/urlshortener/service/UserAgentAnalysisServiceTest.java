package com.urlshortener.service;

import com.urlshortener.common.DeviceTier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentAnalysisServiceTest {

    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String WECHAT_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49";
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
    private static final String ANDROID_MODERN_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String IPHONE_MODERN_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
    private static final String IPAD_MODERN_UA = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";

    private final UserAgentAnalysisService service = new UserAgentAnalysisService();

    private static Map<String, String> headers(String userAgent) {
        Map<String, String> map = new HashMap<>();
        map.put("User-Agent", userAgent);
        return map;
    }

    @Test
    void deviceTierPC() {
        assertThat(service.deviceTier(headers(CHROME_UA))).isEqualTo(DeviceTier.PC);
        assertThat(service.deviceTier(headers(SAFARI_UA))).isEqualTo(DeviceTier.PC);
        assertThat(service.deviceTier(headers(FIREFOX_UA))).isEqualTo(DeviceTier.PC);
    }

    @Test
    void deviceTierMobile() {
        assertThat(service.deviceTier(headers(ANDROID_UA))).isEqualTo(DeviceTier.MOBILE);
        assertThat(service.deviceTier(headers(ANDROID_MODERN_UA))).isEqualTo(DeviceTier.MOBILE);
        assertThat(service.deviceTier(headers(IPHONE_UA))).isEqualTo(DeviceTier.MOBILE);
        assertThat(service.deviceTier(headers(IPHONE_MODERN_UA))).isEqualTo(DeviceTier.MOBILE);
    }

    @Test
    void deviceTierTablet() {
        assertThat(service.deviceTier(headers(IPAD_UA))).isEqualTo(DeviceTier.TABLET);
        assertThat(service.deviceTier(headers(IPAD_MODERN_UA))).isEqualTo(DeviceTier.TABLET);
        assertThat(service.deviceTier(headers(IPADOS_DESKTOP_UA))).isEqualTo(DeviceTier.MOBILE);
    }

    @Test
    void deviceTierEmptyAndNull() {
        assertThat(service.deviceTier(headers(""))).isEqualTo(DeviceTier.PC);
        assertThat(service.deviceTier(new HashMap<>())).isEqualTo(DeviceTier.PC);
    }

    @Test
    void deviceTierFromClientHints() {
        // iPad Safari UA + CH confirming tablet
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", IPAD_UA);
        h.put("Sec-CH-UA-Mobile", "?0");
        h.put("Sec-CH-UA-Platform", "\"iPadOS\"");
        assertThat(service.deviceTier(h)).isEqualTo(DeviceTier.TABLET);

        // Android Chrome UA + CH confirming mobile
        Map<String, String> h2 = new HashMap<>();
        h2.put("User-Agent", ANDROID_UA);
        h2.put("Sec-CH-UA-Mobile", "?1");
        h2.put("Sec-CH-UA-Platform", "\"Android\"");
        assertThat(service.deviceTier(h2)).isEqualTo(DeviceTier.MOBILE);

        // Desktop Chrome UA + CH confirming desktop
        Map<String, String> h3 = new HashMap<>();
        h3.put("User-Agent", CHROME_UA);
        h3.put("Sec-CH-UA-Mobile", "?0");
        h3.put("Sec-CH-UA-Platform", "\"Windows\"");
        assertThat(service.deviceTier(h3)).isEqualTo(DeviceTier.PC);
    }

    @Test
    void matchesOpenTypeAll() {
        assertThat(service.matchesOpenType(0, headers(CHROME_UA))).isTrue();
        assertThat(service.matchesOpenType(0, new HashMap<>())).isTrue();
    }

    @Test
    void matchesOpenTypeClients() {
        assertThat(service.matchesOpenType(1, headers(WECHAT_UA))).isTrue();
        assertThat(service.matchesOpenType(1, headers(CHROME_UA))).isFalse();
        assertThat(service.matchesOpenType(2, headers(DINGTALK_UA))).isTrue();
        assertThat(service.matchesOpenType(2, headers(CHROME_UA))).isFalse();
        assertThat(service.matchesOpenType(6, headers(SAFARI_UA))).isTrue();
        assertThat(service.matchesOpenType(6, headers(CHROME_UA))).isFalse();
        assertThat(service.matchesOpenType(7, headers(CHROME_UA))).isTrue();
        assertThat(service.matchesOpenType(7, headers(FIREFOX_UA))).isFalse();
        assertThat(service.matchesOpenType(8, headers(FIREFOX_UA))).isTrue();
        assertThat(service.matchesOpenType(8, headers(CHROME_UA))).isFalse();
    }

    @Test
    void matchesOpenTypeDeviceBased() {
        assertThat(service.matchesOpenType(3, headers(IPHONE_UA))).isTrue();
        assertThat(service.matchesOpenType(3, headers(ANDROID_UA))).isFalse();
        assertThat(service.matchesOpenType(4, headers(ANDROID_UA))).isTrue();
        assertThat(service.matchesOpenType(4, headers(IPHONE_UA))).isFalse();
        assertThat(service.matchesOpenType(5, headers(IPAD_UA))).isTrue();
        assertThat(service.matchesOpenType(5, headers(ANDROID_UA))).isFalse();
    }

    @Test
    void matchesOpenTypeUnknownValueTolerated() {
        assertThat(service.matchesOpenType(99, headers(CHROME_UA))).isTrue();
    }
}