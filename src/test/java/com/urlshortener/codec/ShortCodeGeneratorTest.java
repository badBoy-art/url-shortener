package com.urlshortener.codec;

import com.urlshortener.config.AppProperties;
import com.urlshortener.service.codec.ShortCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private ShortCodeGenerator generator;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "http://localhost:8080", "key",
                new AppProperties.ShortCode(6, 5),
                new AppProperties.Cache(10, 10000, 24),
                new AppProperties.RateLimit(10, 1),
                new AppProperties.AccessLog(1000, 500, 100000),
                new AppProperties.Auth("test-jwt-secret-0123456789abcdef", 12, 5, 10),
                new AppProperties.Admin("admin", "ohUrlShortener", 50000),
                30000);
        generator = new ShortCodeGenerator(props);
    }

    @Test
    void sameUrlProducesSameCode() {
        assertThat(generator.generate("https://example.com/path?a=1", 0))
                .isEqualTo(generator.generate("https://example.com/path?a=1", 0));
    }

    @Test
    void codeLengthMatchesConfig() {
        String code = generator.generate("https://example.com/anything", 0);
        assertThat(code).hasSize(6);
    }

    @Test
    void differentUrlsProduceDifferentCodes() {
        assertThat(generator.generate("https://example.com/a", 0))
                .isNotEqualTo(generator.generate("https://example.com/b", 0));
    }

    @Test
    void saltedRetryDiffersFromBase() {
        String base = generator.generate("https://example.com/collide", 0);
        String retry = generator.generate("https://example.com/collide", 1);
        assertThat(base).isNotEqualTo(retry);
    }

    @Test
    void urlCacheKeyIsDeterministicHex() {
        String key = generator.urlCacheKey("https://example.com/x");
        assertThat(key).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(key).isEqualTo(generator.urlCacheKey("https://example.com/x"));
        assertThat(key).isNotEqualTo(generator.urlCacheKey("https://example.com/y"));
    }
}
