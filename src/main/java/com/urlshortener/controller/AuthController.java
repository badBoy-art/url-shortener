package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.common.WebUtil;
import com.urlshortener.dto.AuthResponse;
import com.urlshortener.dto.LoginRequest;
import com.urlshortener.ratelimit.RateLimit;
import com.urlshortener.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员认证", description = "管理员登录（账号密码 + 失败锁定）")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "管理员登录", description = "成功返回 JWT，后续管理接口使用 Authorization: Bearer <token>；连续失败 5 次锁定 10 分钟")
    @PostMapping("/login")
    @RateLimit
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(
                request.account(), request.password(), WebUtil.clientIp(httpRequest)));
    }
}
