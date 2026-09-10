package com.urlshortener.controller;

import com.urlshortener.common.BusinessException;
import com.urlshortener.common.UserAgentMatcher;
import com.urlshortener.entity.ShortLink;
import com.urlshortener.ratelimit.RateLimit;
import com.urlshortener.service.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
@Tag(name = "短链重定向")
public class RedirectController {

    private final RedirectService redirectService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @GetMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}")
    @Operation(summary = "访问短链",
            description = "302 跳转目标地址；多目标短链按 X-Client-Type/X-Platform 请求头与 User-Agent 设备类型（pc/mobile/tablet）选择对应 destUrl（无匹配回退主目标）；"
                    + "密码保护链接返回验证页；打开方式不匹配返回提示页；过期/停用返回 410")
    public ResponseEntity<?> redirect(@PathVariable String code, HttpServletRequest request) {
        ShortLink link = validateLink(code);
        String destUrl = redirectService.resolveDestUrl(link, request);
        if (link.getPasswordHash() != null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(render("verify.html", code, null));
        }
        if (openTypeRejected(link, request)) {
            return unsupportedPage(code);
        }
        redirectService.recordVisit(code, request);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destUrl)).build();
    }

    @PostMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}/verify")
    @RateLimit
    @Operation(summary = "密码验证", description = "验证页表单提交，密码正确则 302 跳转；支持 X-Client-Type/X-Platform 请求头与 User-Agent 选择多目标")
    public ResponseEntity<?> verify(@PathVariable String code,
                                    @RequestParam("password") String password,
                                    HttpServletRequest request) {
        ShortLink link = validateLink(code);
        String destUrl = redirectService.resolveDestUrl(link, request);
        if (link.getPasswordHash() == null || passwordEncoder.matches(password, link.getPasswordHash())) {
            if (openTypeRejected(link, request)) {
                return unsupportedPage(code);
            }
            redirectService.recordVisit(code, request);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destUrl)).build();
        }
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                .body(render("verify.html", code, "密码错误，请重试"));
    }

    private ShortLink validateLink(String code) {
        ShortLink link = redirectService.resolve(code)
                .orElseThrow(() -> BusinessException.notFound("短链 " + code + " 不存在"));
        if (link.isExpired(LocalDateTime.now())) {
            throw BusinessException.gone("短链已过期");
        }
        if (!link.isEnabled()) {
            throw BusinessException.gone("短链已停用");
        }
        return link;
    }

    private boolean openTypeRejected(ShortLink link, HttpServletRequest request) {
        int openType = link.getOpenType() == null ? 0 : link.getOpenType();
        return !UserAgentMatcher.matches(openType, request.getHeader("User-Agent"));
    }

    private ResponseEntity<String> unsupportedPage(String code) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                .body(render("unsupported.html", code, null));
    }

    /** 模板读取 + 占位符替换；verify.html 支持 {{CODE}}/{{ERROR}}，unsupported.html 支持 {{CODE}} */
    private String render(String templatePath, String code, String error) {
        String template = templateCache.computeIfAbsent(templatePath, this::loadTemplate);
        String errorHtml = error == null ? "" : "<div class=\"error\">" + error + "</div>";
        return template.replace("{{CODE}}", code).replace("{{ERROR}}", errorHtml);
    }

    private String loadTemplate(String path) {
        try (InputStream in = new ClassPathResource("templates/" + path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path + " 模板读取失败", e);
        }
    }
}
