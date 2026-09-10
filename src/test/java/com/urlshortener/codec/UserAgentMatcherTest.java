package com.urlshortener.codec;

import com.urlshortener.common.DeviceTier;
import com.urlshortener.common.UserAgentMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentMatcherTest {

    @Test
    void deviceTierFromHints() {
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", "\"iPadOS\"", null)).isEqualTo(DeviceTier.TABLET);
        assertThat(UserAgentMatcher.deviceTierFromHints("?1", "\"iOS\"", null)).isEqualTo(DeviceTier.MOBILE);
        // iPad 桌面模式：iOS 平台 + ?0
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", "\"iOS\"", null)).isEqualTo(DeviceTier.TABLET);
        assertThat(UserAgentMatcher.deviceTierFromHints("?1", "\"iOS\"", "\"iPad13,4\"")).isEqualTo(DeviceTier.TABLET);
        assertThat(UserAgentMatcher.deviceTierFromHints("?1", "\"Android\"", null)).isEqualTo(DeviceTier.MOBILE);
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", "\"macOS\"", null)).isEqualTo(DeviceTier.PC);
        // iPadOS 桌面模式上报 macOS + 型号
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", "\"macOS\"", "\"iPad12,1\"")).isEqualTo(DeviceTier.TABLET);
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", "\"Windows\"", null)).isEqualTo(DeviceTier.PC);
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", "\"Chrome OS\"", null)).isEqualTo(DeviceTier.PC);
        assertThat(UserAgentMatcher.deviceTierFromHints("?1", null, null)).isEqualTo(DeviceTier.MOBILE);
        assertThat(UserAgentMatcher.deviceTierFromHints("?0", null, null)).isEqualTo(DeviceTier.PC);
        assertThat(UserAgentMatcher.deviceTierFromHints("1", "android", null)).isEqualTo(DeviceTier.MOBILE);
        assertThat(UserAgentMatcher.deviceTierFromHints(null, null, null)).isEqualTo(DeviceTier.UNKNOWN);
    }
}