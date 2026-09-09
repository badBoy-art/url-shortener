package com.urlshortener.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.entity.ShortLink;
import com.urlshortener.mapper.ShortLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void functionalTestProperties(DynamicPropertyRegistry registry) {
        // 功能测试不触发限流（限流由 RateLimitIntegrationTest 单独验证）
        registry.add("app.rate-limit.limit", () -> "1000");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ShortLinkMapper linkMapper;

    @BeforeEach
    void disableRedirectFollowing() {
        rest.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
    }

    @Test
    void createThenRedirect() throws Exception {
        String dest = "https://www.example.com/some/long/path?x=1&y=2";
        String code = codeOf(create(dest));
        assertThat(code).hasSize(6);

        ResponseEntity<String> resp = rest.getForEntity("/" + code, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resp.getHeaders().getLocation()).isEqualTo(URI.create(dest));
    }

    @Test
    void sameUrlReturnsSameCode() throws Exception {
        String dest = "https://www.example.com/idempotent";
        String first = codeOf(create(dest));
        String second = codeOf(create(dest));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void autoCreateReusesCustomCodeOfSameUrl() throws Exception {
        String dest = "https://www.example.com/custom-then-auto";
        String custom = codeOf(create(Map.of(
                "destUrl", dest,
                "shortCode", "CustAuto")));
        assertThat(custom).isEqualTo("CustAuto");

        // 同 URL 不带短码再次创建，应复用已有自定义短码
        assertThat(codeOf(create(dest))).isEqualTo("CustAuto");
    }

    @Test
    void customCodeAndConflict() throws Exception {
        String c = codeOf(create(Map.of(
                "destUrl", "https://www.example.com/custom1",
                "shortCode", "myCode1")));
        assertThat(c).isEqualTo("myCode1");

        ResponseEntity<String> conflict = create(Map.of(
                "destUrl", "https://www.example.com/custom2",
                "shortCode", "myCode1"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> invalid = create(Map.of(
                "destUrl", "https://www.example.com/custom3",
                "shortCode", "abc0")); // 含非法字符 0
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidDestUrlRejected() {
        assertThat(create(Map.of("destUrl", "javascript:alert(1)"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create(Map.of("destUrl", "not-a-url"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create(Map.of("destUrl", ""))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create(Map.of("destUrl", "ftp://example.com"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownCodeReturns404() {
        ResponseEntity<String> resp = rest.getForEntity("/zzzzzz", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void expiredLinkReturns410() throws Exception {
        String past = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertThat(create(Map.of(
                "destUrl", "https://www.example.com/expired-past",
                "expiredAt", past)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String soon = LocalDateTime.now().plusSeconds(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String code = codeOf(create(Map.of(
                "destUrl", "https://www.example.com/expired-soon",
                "expiredAt", soon)));
        Thread.sleep(1500);
        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    void passwordProtectedFlow() throws Exception {
        String dest = "https://www.example.com/secret-page";
        String code = codeOf(create(Map.of(
                "destUrl", dest,
                "password", "secret123")));

        ResponseEntity<String> page = rest.getForEntity("/" + code, String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getHeaders().getContentType().toString()).contains("text/html");
        assertThat(page.getBody()).contains("密码");

        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> wrong = rest.exchange("/" + code + "/verify", HttpMethod.POST,
                new HttpEntity<>("password=nope", formHeaders), String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> right = rest.exchange("/" + code + "/verify", HttpMethod.POST,
                new HttpEntity<>("password=secret123", formHeaders), String.class);
        assertThat(right.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(right.getHeaders().getLocation()).isEqualTo(URI.create(dest));
    }

    @Test
    void deleteRequiresApiKey() throws Exception {
        String code = codeOf(create("https://www.example.com/to-delete"));

        assertThat(rest.exchange("/api/v1/links/" + code, HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "test-key");
        ResponseEntity<String> deleted = rest.exchange("/api/v1/links/" + code, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentCreatesOfSameUrlYieldSingleCode() throws Exception {
        String dest = "https://www.example.com/concurrent";
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = pool.invokeAll(Collections.nCopies(threads,
                    () -> codeOf(create(dest))));
            Set<String> codes = new HashSet<>();
            for (Future<String> future : futures) {
                codes.add(future.get());
            }
            assertThat(codes).hasSize(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void statsAfterClicks() throws Exception {
        String code = codeOf(create("https://www.example.com/stats-target"));
        HttpHeaders ip1 = new HttpHeaders();
        ip1.set("X-Forwarded-For", "1.2.3.4");
        HttpHeaders ip2 = new HttpHeaders();
        ip2.set("X-Forwarded-For", "5.6.7.8");

        assertThat(rest.exchange("/" + code, HttpMethod.GET, new HttpEntity<>(ip1), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(rest.exchange("/" + code, HttpMethod.GET, new HttpEntity<>(ip2), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FOUND);

        JsonNode stats = null;
        for (int i = 0; i < 30; i++) {
            stats = objectMapper.readTree(
                    rest.getForEntity("/api/v1/links/" + code + "/stats", String.class).getBody());
            if (stats.get("data").get("todayPv").asLong() >= 2
                    && stats.get("data").get("totalClicks").asLong() >= 2) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(stats.get("data").get("todayPv").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("data").get("todayUv").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("data").get("totalClicks").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("data").get("hourly").size()).isGreaterThan(0);
    }

    @Test
    void pubSubEvictInvalidatesLocalCache() throws Exception {
        String code = codeOf(create("https://www.example.com/evict-before"));
        // 预热本地缓存
        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);

        // 绕过缓存直接改库，模拟另一个实例上发生的变更
        ShortLink link = linkMapper.selectOne(
                Wrappers.<ShortLink>lambdaQuery().eq(ShortLink::getShortCode, code));
        link.setDestUrl("https://www.example.com/evict-after");
        linkMapper.updateById(link);

        // 本地缓存仍命中旧值（这就是多实例下其它实例曾面临的过期问题）
        assertThat(rest.getForEntity("/" + code, String.class).getHeaders().getLocation())
                .isEqualTo(URI.create("https://www.example.com/evict-before"));

        // 模拟其它实例的 evict()：删 Redis 键 + 广播失效消息
        redis.delete("link:" + code);
        redis.convertAndSend("link:evict", code);

        ResponseEntity<String> fresh = null;
        for (int i = 0; i < 20; i++) {
            fresh = rest.getForEntity("/" + code, String.class);
            if (fresh.getHeaders().getLocation() != null
                    && fresh.getHeaders().getLocation().toString().contains("evict-after")) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(fresh.getHeaders().getLocation())
                .isEqualTo(URI.create("https://www.example.com/evict-after"));
    }

    private ResponseEntity<String> create(String destUrl) {
        return create(Map.of("destUrl", destUrl));
    }

    private ResponseEntity<String> create(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/links", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String codeOf(ResponseEntity<String> resp) throws Exception {
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = objectMapper.readTree(resp.getBody());
        assertThat(node.get("code").asInt()).isZero();
        return node.get("data").get("shortCode").asText();
    }
}
