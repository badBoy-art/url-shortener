package com.urlshortener.service.codec;

import com.urlshortener.common.Base58;
import com.urlshortener.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 确定性短码生成：Base58(SHA-256(destUrl)) 前 N 位。
 * 同一 URL 恒得到相同候选码；冲突时以 destUrl + ":" + attempt 加盐重试，
 * 与 ohUrlShortener 的哈希思路一致。
 */
@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    private final AppProperties props;

    public String generate(String destUrl, int attempt) {
        String input = attempt == 0 ? destUrl : destUrl + ":" + attempt;
        String encoded = Base58.encode(sha256(input));
        return encoded.substring(0, Math.min(props.shortCode().length(), encoded.length()));
    }

    public int maxRetries() {
        return props.shortCode().maxRetries();
    }

    /** url -> code 缓存的 key（SHA-256 十六进制） */
    public String urlCacheKey(String destUrl) {
        return HexFormat.of().formatHex(sha256(destUrl));
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
