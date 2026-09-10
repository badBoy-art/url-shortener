package com.urlshortener.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiDestIntegrationTest extends AbstractIntegrationTest {

    private static final String PC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String ANDROID_UA = "Mozilla/5.0 (Linux; Android/14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String IPAD_UA = "Mozilla/5.0 (iPad/17.5; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
    private static final String IPADOS_DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/13.0 Safari/605.1.15 Mobile/15E148";

    @DynamicPropertySource
    static void multiDestProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.limit", () -> "1000");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @LocalServerPort
    int port;

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
    void deviceUaSelectsDestinationOnRedirect() throws Exception {
        String pc = "https://www.example.com/pc/page";
        String mobile = "https://m.example.com/page";
        String tablet = "https://pad.example.com/page";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile),
                        Map.of("label", "tablet", "destUrl", tablet)))));

        // PC UA → pc 目标
        assertThat(getWithHeaders("/" + code, Map.of(), PC_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(pc));

        // Android UA → mobile 目标
        assertThat(getWithHeaders("/" + code, Map.of(), ANDROID_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(mobile));

        // iPad UA → tablet 目标
        assertThat(getWithHeaders("/" + code, Map.of(), IPAD_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(tablet));

        // iPadOS 13+ 桌面模式 UA → tablet 目标
        assertThat(getWithHeaders("/" + code, Map.of(), IPADOS_DESKTOP_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(tablet));
    }

    @Test
    void clientHintsSelectDestinationOnRedirect() throws Exception {
        String pc = "https://www.example.com/ch-pc";
        String tablet = "https://pad.example.com/ch-tablet";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "tablet", "destUrl", tablet)))));

        // Client Hints（Sec-CH-UA-Platform: iPadOS）优先于 PC UA → tablet 目标。
        // 注：TestRestTemplate 底层 HttpURLConnection 会静默丢弃 Sec-* 请求头，此处改用 JDK HttpClient
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/" + code))
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"iPadOS\"")
                .header("User-Agent", PC_UA)
                .GET()
                .build();
        HttpResponse<Void> response = HttpClient.newBuilder().build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.headers().firstValue("Location")).hasValue(tablet);

        // 无 Client Hints 时回退 UA：PC UA → pc 目标
        assertThat(getWithHeaders("/" + code, Map.of(), PC_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(pc));
    }

    @Test
    void appHeadersOverrideUaAndFallback() throws Exception {
        String pc = "https://www.example.com/app-pc";
        String mobile = "https://m.example.com/app-mobile";
        String app = "https://app.example.com/launch";
        String ios = "https://ios.example.com/launch";
        String android = "https://android.example.com/launch";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile),
                        Map.of("label", "app", "destUrl", app),
                        Map.of("label", "ios", "destUrl", ios),
                        Map.of("label", "android", "destUrl", android)))));

        // X-Client-Type 优先于 UA
        assertThat(getWithHeaders("/" + code, Map.of("X-Client-Type", "app"), ANDROID_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(app));

        // X-Platform 优先于 UA
        assertThat(getWithHeaders("/" + code, Map.of("X-Platform", "ios"), ANDROID_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(ios));

        // 未命中跳过继续匹配：wechat 未命中 → android 命中
        assertThat(getWithHeaders("/" + code, Map.of("X-Client-Type", "wechat", "X-Platform", "android"), ANDROID_UA)
                .getHeaders().getLocation()).isEqualTo(URI.create(android));

        // 旧 X-Dest-Label 请求头已废弃：携带也不影响，按 UA 选择
        assertThat(getWithHeaders("/" + code, Map.of("X-Dest-Label", "mobile"), PC_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(pc));
    }

    @Test
    void fallbackToPrimaryWhenNoLabelMatches() throws Exception {
        String primary = "https://www.example.com/fallback-primary";
        String ios = "https://ios.example.com/fallback";
        String code = codeOf(create(Map.of(
                "destUrl", primary,
                "destinations", List.of(
                        Map.of("label", "ios", "destUrl", ios)))));

        // PC UA 无匹配 label → 回退主目标
        assertThat(getWithHeaders("/" + code, Map.of(), PC_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(primary));

        // 命中 ios label → ios 目标
        assertThat(getWithHeaders("/" + code, Map.of("X-Platform", "ios"), ANDROID_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(ios));
    }

    @Test
    void onlyDestinationsWithoutDestUrl() throws Exception {
        String pc = "https://www.example.com/pc-only";
        String mobile = "https://m.example.com/mobile-only";
        String code = codeOf(create(Map.of(
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile)))));

        // PC UA：第一个目标为主目标
        assertThat(getWithHeaders("/" + code, Map.of(), PC_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(pc));
        assertThat(getWithHeaders("/" + code, Map.of(), ANDROID_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(mobile));
    }

    @Test
    void infoEndpointResolvesByHeaders() throws Exception {
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

        // X-Platform + 移动 UA → destUrl 返回 mobile 目标
        JsonNode mobileInfo = dataOf(rest.exchange("/api/url/" + code, HttpMethod.GET,
                new HttpEntity<>(headersOf(Map.of("X-Platform", "mobile"), ANDROID_UA)), String.class));
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

        // 带 App 请求头也不影响（忽略，回退主目标）
        assertThat(getWithHeaders("/" + code, Map.of("X-Client-Type", "pc"), PC_UA).getHeaders().getLocation())
                .isEqualTo(URI.create(dest));

        // 查询信息正常，destinations 为 null/空
        JsonNode info = dataOf(rest.getForEntity("/api/url/" + code, String.class));
        assertThat(info.get("destUrl").asText()).isEqualTo(dest);
        assertThat(info.get("destinations").isNull() || info.get("destinations").isEmpty()).isTrue();
    }

    @Test
    void passwordProtectedMultiDestVerifyHonorsHeaders() throws Exception {
        String pc = "https://www.example.com/secret-pc";
        String mobile = "https://m.example.com/secret-mobile";
        String code = codeOf(create(Map.of(
                "destUrl", pc,
                "password", "secret123",
                "destinations", List.of(
                        Map.of("label", "pc", "destUrl", pc),
                        Map.of("label", "mobile", "destUrl", mobile)))));

        HttpHeaders headers = headersOf(Map.of("X-Platform", "mobile"), ANDROID_UA);
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
        assertThat(getWithHeaders("/" + again, Map.of(), ANDROID_UA).getHeaders().getLocation())
                .isEqualTo(URI.create("https://m.example.com/del-multi"));
    }

    private HttpHeaders headersOf(Map<String, String> headers, String ua) {
        HttpHeaders h = new HttpHeaders();
        headers.forEach(h::set);
        if (ua != null) {
            h.set("User-Agent", ua);
        }
        return h;
    }

    private ResponseEntity<String> getWithHeaders(String path, Map<String, String> headers, String ua) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headersOf(headers, ua)), String.class);
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
