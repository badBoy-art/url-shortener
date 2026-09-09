package com.urlshortener.ratelimit;

import com.urlshortener.common.BusinessException;
import com.urlshortener.security.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 旧版管理接口（DELETE /api/v1/links/{code}、全局统计等 @ApiKeyRequired 标注的接口）
 * 鉴权：X-API-Key 或 Bearer JWT 任一有效。
 */
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final AdminAccess adminAccess;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean required = handlerMethod.hasMethodAnnotation(ApiKeyRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(ApiKeyRequired.class);
        if (!required) {
            return true;
        }
        if (!adminAccess.isAuthorized(request)) {
            throw BusinessException.unauthorized("无效的 API Key");
        }
        return true;
    }
}
