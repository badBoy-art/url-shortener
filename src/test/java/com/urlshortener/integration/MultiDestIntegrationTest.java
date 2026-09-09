package com.urlshortener.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiDestIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void multiDestProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.limit", () -> "1000");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

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
    void headerSelectsDestinationOnRedirect() throws Exception {
        String pc = "https://www.example.com/pc/page";
        String mobile = "https://m.example.com/page";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile)))));

        // 无 header：回退主目标
        assertThat(rest.getForEntity("/" + code, String.class).getHeaders().getLocation())
                .isEqualTo(URI.create(pc));

        // header=pc → pc 目标
        assertThat(getWithLabel("/" + code, "pc").getHeaders().getLocation())
                .isEqualTo(URI.create(pc));

        // header=mobile → 移动端目标
        assertThat(getWithLabel("/" + code, "mobile").getHeaders().getLocation())
                .isEqualTo(URI.create(mobile));

        // header 值无匹配 → 回退主目标
        assertThat(getWithLabel("/" + code, "tablet").getHeaders().getLocation())
                .isEqualTo(URI.create(pc));
    }

    @Test
    void onlyDestinationsWithoutDestUrl() throws Exception {
        String pc = "https://www.example.com/pc-only";
        String mobile = "https://m.example.com/mobile-only";
        String code = codeOf(create(Map.of(
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile)))));

        // 无 header：第一个目标为主目标
        assertThat(rest.getForEntity("/" + code, String.class).getHeaders().getLocation())
                .isEqualTo(URI.create(pc));
        assertThat(getWithLabel("/" + code, "mobile").getHeaders().getLocation())
                .isEqualTo(URI.create(mobile));
    }

    @Test
    void infoEndpointHonorsHeader() throws Exception {
        String pc = "https://www.example.com/info-pc";
        String mobile = "https://m.example.com/info-mobile";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile)))));

        JsonNode plain = dataOf(rest.getForEntity("/api/url/" + code, String.class));
        assertThat(plain.get("destUrl").asText()).isEqualTo(pc);
        assertThat(plain.get("destinations").size()).isEqualTo(2);

        JsonNode mobileInfo = dataOf(rest.exchange("/api/url/" + code, HttpMethod.GET,
                new HttpEntity<>(labelHeaders("mobile")), String.class));
        assertThat(mobileInfo.get("destUrl").asText()).isEqualTo(mobile);
        assertThat(mobileInfo.get("destinations").size()).isEqualTo(2);
    }

    @Test
    void legacySingleDestLinkStillWorks() throws Exception {
        String dest = "https://www.example.com/legacy-single";
        String code = codeOf(create(Map.of("destUrl", dest)));

        // 老式短链：无 header 正常跳转
        assertThat(rest.getForEntity("/" + code, String.class).getHeaders().getLocation())
                .isEqualTo(URI.create(dest));

        // 带 header 也不影响（忽略，回退主目标）
        assertThat(getWithLabel("/" + code, "pc").getHeaders().getLocation())
                .isEqualTo(URI.create(dest));

        // 查询信息正常，destinations 为 null/空
        JsonNode info = dataOf(rest.getForEntity("/api/url/" + code, String.class));
        assertThat(info.get("destUrl").asText()).isEqualTo(dest);
        assertThat(info.get("destinations").isNull() || info.get("destinations").isEmpty()).isTrue();
    }

    @Test
    void passwordProtectedMultiDestVerifyHonorsHeader() throws Exception {
        String pc = "https://www.example.com/secret-pc";
        String mobile = "https://m.example.com/secret-mobile";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "password", "secret123",
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile)))));

        HttpHeaders headers = labelHeaders("mobile");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> ok = rest.exchange("/" + code + "/verify", HttpMethod.POST,
                new HttpEntity<>("password=secret123", headers), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(ok.getHeaders().getLocation()).isEqualTo(URI.create(mobile));
    }

    @Test
    void duplicateLabelRejected() {
        ResponseEntity<String> resp = create(Map.of(
                "destUrl", "https://www.example.com/dup-label",
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", "https://www.example.com/a"),
                        Map.of("label", "pc", "destUrl", "https://www.example.com/b"))));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void neitherDestUrlNorDestinationsRejected() {
        assertThat(create(Map.of("description", "nothing")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void samePrimaryUrlIsIdempotentWithDestinations() throws Exception {
        String pc = "https://www.example.com/idem-multi";
        String first = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", "https://m.example.com/idem-multi")))));
        String second = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", "https://m.example.com/idem-multi")))));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void deleteRemovesDestinations() throws Exception {
        String code = codeOf(create(Map.of(
                "destUrl", "https://www.example.com/del-multi",
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", "https://www.example.com/del-multi-pc"),
                        Map.of("label", "mobile", "destUrl", "https://m.example.com/del-multi")))));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "test-key");
        assertThat(rest.exchange("/api/url/" + code, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // 重建同主目标短链可再次成功（旧 destinations 已随删除清理，无残留冲突）
        String again = codeOf(create(Map.of(
                "destUrl", "https://www.example.com/del-multi",
                "destinations", List.of(
                        Map.of("label", "mobile", "destUrl", "https://m.example.com/del-multi")))));
        assertThat(again).isEqualTo(code);
        assertThat(getWithLabel("/" + again, "mobile").getHeaders().getLocation())
                .isEqualTo(URI.create("https://m.example.com/del-multi"));
    }

    private HttpHeaders labelHeaders(String label) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dest-Label", label);
        return headers;
    }

    private ResponseEntity<String> getWithLabel(String path, String label) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(labelHeaders(label)), String.class);
    }

    private ResponseEntity<String> create(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/url", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String codeOf(ResponseEntity<String> resp) throws Exception {
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = objectMapper.readTree(resp.getBody());
        assertThat(node.get("code").asInt()).isZero();
        return node.get("data").get("shortCode").asText();
    }

    private JsonNode dataOf(ResponseEntity<String> resp) throws Exception {
        JsonNode node = objectMapper.readTree(resp.getBody());
        assertThat(node.get("code").asInt()).isZero();
        return node.get("data");
    }
}
