package com.urlshortener.codec;

import com.urlshortener.common.Base58;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class Base58Test {

    @Test
    void emptyInputEncodesToEmpty() {
        assertThat(Base58.encode(new byte[0])).isEmpty();
    }

    @Test
    void leadingZeroByteBecomesOne() {
        assertThat(Base58.encode(new byte[]{0})).isEqualTo("1");
        assertThat(Base58.encode(new byte[]{0, 0, 1})).startsWith("11");
    }

    @Test
    void outputUsesOnlyAlphabetChars() {
        byte[] data = new byte[32];
        new Random(42).nextBytes(data);
        String encoded = Base58.encode(data);
        assertThat(encoded).isNotEmpty();
        for (char c : encoded.toCharArray()) {
            assertThat(Base58.ALPHABET.indexOf(c)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void sha256BytesEncodeToExpectedLength() {
        byte[] digest = sha256("hello world");
        assertThat(Base58.encode(digest)).hasSizeGreaterThanOrEqualTo(43).hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void deterministicEncoding() {
        byte[] data = "some deterministic input".getBytes(StandardCharsets.UTF_8);
        assertThat(Base58.encode(data)).isEqualTo(Base58.encode(data));
    }

    @Test
    void codeValidation() {
        assertThat(Base58.isValidCode("abc123")).isTrue();
        assertThat(Base58.isValidCode("1")).isFalse();
        assertThat(Base58.isValidCode("abcdefghijklmnopq")).isFalse(); // 17 位超长
        assertThat(Base58.isValidCode("abco")).isTrue();   // 小写 o 在字母表中
        assertThat(Base58.isValidCode("abc0")).isFalse();  // 数字 0
        assertThat(Base58.isValidCode("abcO")).isFalse();  // 大写 O
        assertThat(Base58.isValidCode("abcI")).isFalse();  // 大写 I
        assertThat(Base58.isValidCode("abcl")).isFalse();  // 小写 l
        assertThat(Base58.isValidCode("a-bc")).isFalse();  // 非法字符
        assertThat(Base58.isValidCode(null)).isFalse();
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
