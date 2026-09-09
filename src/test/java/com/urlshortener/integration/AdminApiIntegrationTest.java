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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiIntegrationTest extends AbstractIntegrationTest {

    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String WECHAT_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49";
    private static final String PLAIN_UA = "curl/8.4.0";

    @DynamicPropertySource
    static void adminTestProperties(DynamicPropertyRegistry registry) {
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
    void loginSuccessReturnsJwt() throws Exception {
        ResponseEntity<String> resp = login("admin", "ohUrlShortener", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = dataOf(resp);
        assertThat(data.get("token").asText()).isNotBlank();
        assertThat(data.get("account").asText()).isEqualTo("admin");
    }

    @Test
    void loginWrongPasswordReturns401() {
        assertThat(login("admin", "definitely-wrong-pass", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lockoutAfterRepeatedFailures() {
        // 使用不存在的账号 + 独立 X-Forwarded-For，避免污染 admin 账号与本机 IP 的失败计数
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.66.66.66");
        for (int i = 0; i < 5; i++) {
            assertThat(login("nosuchaccount99", "wrongpass123", headers).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(login("nosuchaccount99", "wrongpass123", headers).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // 锁定按账号 + IP 生效：其它 IP 登录不受影响
        assertThat(login("admin", "ohUrlShortener", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminEndpointsRequireAuth() {
        assertThat(rest.getForEntity("/api/urls", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/account", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/logs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/stats/overview", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminAccessAcceptsJwtAndApiKey() throws Exception {
        String token = tokenOf(login("admin", "ohUrlShortener", null));

        ResponseEntity<String> withJwt = rest.exchange("/api/urls", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);
        assertThat(withJwt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(withJwt).has("total")).isTrue();

        HttpHeaders keyHeaders = new HttpHeaders();
        keyHeaders.set("X-API-Key", "test-key");
        ResponseEntity<String> withKey = rest.exchange("/api/urls", HttpMethod.GET,
                new HttpEntity<>(keyHeaders), String.class);
        assertThat(withKey.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(rest.exchange("/api/stats/overview", HttpMethod.GET,
                new HttpEntity<>(keyHeaders), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminListLinksAndKeywordSearch() throws Exception {
        codeOf(create(Map.of("destUrl", "https://www.example.com/admin-list-1",
                "description", "keyword-hit-alpha")));
        codeOf(create(Map.of("destUrl", "https://www.example.com/admin-list-2",
                "description", "beta-page")));
        String token = tokenOf(login("admin", "ohUrlShortener", null));

        JsonNode all = dataOf(rest.exchange("/api/urls?page=1&size=50", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class));
        assertThat(all.get("total").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode hit = dataOf(rest.exchange("/api/urls?keyword=keyword-hit", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class));
        assertThat(hit.get("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(hit.get("list").toString()).contains("admin-list-1");
    }

    @Test
    void disableAndReenableLink() throws Exception {
        String code = codeOf(create("https://www.example.com/switch-status"));
        String token = tokenOf(login("admin", "ohUrlShortener", null));

        assertThat(rest.exchange("/api/url/" + code + "/change_state", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enable", false), bearerJson(token)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode())
                .isEqualTo(HttpStatus.GONE);

        assertThat(rest.exchange("/api/url/" + code + "/change_state", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enable", true), bearerJson(token)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/" + code, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void openTypeChromeOnlyRedirect() throws Exception {
        String code = codeOf(create(Map.of(
                "destUrl", "https://www.example.com/chrome-only",
                "openType", 7)));

        ResponseEntity<String> rejected = getWithUa("/" + code, PLAIN_UA);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rejected.getBody()).contains("不支持的打开方式");

        ResponseEntity<String> ok = getWithUa("/" + code, CHROME_UA);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(ok.getHeaders().getLocation())
                .isEqualTo(URI.create("https://www.example.com/chrome-only"));
    }

    @Test
    void openTypeWechatOnlyRedirect() throws Exception {
        String code = codeOf(create(Map.of(
                "destUrl", "https://www.example.com/wechat-only",
                "openType", 1)));

        assertThat(getWithUa("/" + code, PLAIN_UA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> ok = getWithUa("/" + code, WECHAT_UA);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(ok.getHeaders().getLocation())
                .isEqualTo(URI.create("https://www.example.com/wechat-only"));
    }

    @Test
    void accessLogQueryAndExcelExport() throws Exception {
        String code = codeOf(create("https://www.example.com/log-export"));
        clickWithIp(code, "9.9.9.1");
        clickWithIp(code, "9.9.9.2");
        String token = tokenOf(login("admin", "ohUrlShortener", null));

        JsonNode logs = null;
        for (int i = 0; i < 30; i++) {
            logs = dataOf(rest.exchange("/api/logs?code=" + code, HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class));
            if (logs.get("total").asLong() >= 2) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(logs.get("total").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(logs.get("uniqueIpCount").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(logs.get("list").size()).isGreaterThanOrEqualTo(2);

        ResponseEntity<byte[]> export = rest.exchange("/api/logs/export?code=" + code,
                HttpMethod.GET, new HttpEntity<>(bearer(token)), byte[].class);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getHeaders().getContentType().toString()).contains("spreadsheetml");
        byte[] body = export.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(4);
        // xlsx 是 zip 容器，魔数 PK
        assertThat(new String(body, 0, 2)).isEqualTo("PK");
    }

    @Test
    void adminUserCrudFlow() throws Exception {
        String token = tokenOf(login("admin", "ohUrlShortener", null));

        JsonNode users = dataOf(rest.exchange("/api/account", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class));
        assertThat(users.get("list").toString()).contains("admin");

        ResponseEntity<String> created = rest.exchange("/api/account", HttpMethod.POST,
                new HttpEntity<>(Map.of("account", "auditor01", "password", "audit12345"),
                        bearerJson(token)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(created).get("account").asText()).isEqualTo("auditor01");

        assertThat(rest.exchange("/api/account", HttpMethod.POST,
                new HttpEntity<>(Map.of("account", "auditor01", "password", "audit12345"),
                        bearerJson(token)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(rest.exchange("/api/account/auditor01/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("password", "newpass12345"), bearerJson(token)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(rest.exchange("/api/account/ghostuser99/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("password", "newpass12345"), bearerJson(token)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> newLogin = login("auditor01", "newpass12345", null);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(newLogin).get("account").asText()).isEqualTo("auditor01");
    }

    @Test
    void statsContainNewDimensions() throws Exception {
        String code = codeOf(create("https://www.example.com/stats-dims"));
        clickWithIp(code, "7.7.7.7");
        String token = tokenOf(login("admin", "ohUrlShortener", null));

        JsonNode stats = null;
        for (int i = 0; i < 30; i++) {
            stats = dataOf(rest.getForEntity("/api/url/" + code + "/stats", String.class));
            if (stats.get("todayPv").asLong() >= 1) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(stats.get("todayPv").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.has("yesterdayPv")).isTrue();
        assertThat(stats.has("yesterdayUv")).isTrue();
        assertThat(stats.get("last7DaysPv").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("last7DaysUv").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("monthlyPv").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("monthlyUv").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode overview = null;
        for (int i = 0; i < 30; i++) {
            overview = dataOf(rest.exchange("/api/stats/overview", HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class));
            if (overview.get("todayPv").asLong() >= 1) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(overview.get("totalLinks").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(overview.has("yesterdayPv")).isTrue();
        assertThat(overview.get("last7DaysPv").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(overview.get("monthlyPv").asLong()).isGreaterThanOrEqualTo(1);
    }

    private void clickWithIp(String code, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", ip);
        ResponseEntity<String> resp = rest.exchange("/" + code, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    }

    private ResponseEntity<String> getWithUa(String path, String ua) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", ua);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> login(String account, String password, HttpHeaders extra) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (extra != null) {
            headers.putAll(extra);
        }
        return rest.exchange("/api/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("account", account, "password", password), headers), String.class);
    }

    private ResponseEntity<String> create(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/url", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> create(String destUrl) {
        return create(Map.of("destUrl", destUrl));
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

    private String tokenOf(ResponseEntity<String> loginResp) throws Exception {
        return dataOf(loginResp).get("token").asText();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private HttpHeaders bearerJson(String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
