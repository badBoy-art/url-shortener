package com.urlshortener.common;

/**
 * 设备类型三档：pc / mobile / tablet。
 * UNKNOWN 表示 Client Hints 信息不足，需回退到 User-Agent 识别。
 */
public enum DeviceTier {
    UNKNOWN,
    PC,
    MOBILE,
    TABLET
}
