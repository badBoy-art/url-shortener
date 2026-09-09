package com.urlshortener.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void rateLimitProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.limit", () -> "5");
        registry.add("app.rate-limit.window-seconds", () -> "1");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void creationIsRateLimitedPerIp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "10.9.9.9");

        int passed = 0;
        int limited = 0;
        for (int i = 0; i < 12; i++) {
            ResponseEntity<String> resp = rest.exchange("/api/v1/links", HttpMethod.POST,
                    new HttpEntity<>(Map.of("destUrl", "https://example.com/rl/" + i), headers),
                    String.class);
            if (resp.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                limited++;
            } else {
                passed++;
            }
        }
        assertThat(passed).isEqualTo(5);
        assertThat(limited).isEqualTo(7);
    }
}
