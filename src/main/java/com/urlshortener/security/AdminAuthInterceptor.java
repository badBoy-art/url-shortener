package com.urlshortener.security;

import com.urlshortener.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 保护 /api/urls、/api/logs/**、/api/account/**、/api/stats/overview 的鉴权拦截器：X-API-Key 或 Bearer JWT 任一有效。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAccess adminAccess;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (!adminAccess.isAuthorized(request)) {
            throw BusinessException.unauthorized("未认证：请携带有效的 X-API-Key 或 Bearer Token");
        }
        return true;
    }
}
