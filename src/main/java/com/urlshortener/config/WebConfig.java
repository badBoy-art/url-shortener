package com.urlshortener.config;

import com.urlshortener.ratelimit.ApiKeyInterceptor;
import com.urlshortener.ratelimit.RateLimitInterceptor;
import com.urlshortener.security.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final ApiKeyInterceptor apiKeyInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/**");
        registry.addInterceptor(apiKeyInterceptor).addPathPatterns("/**");
        // /api/* 下的管理接口统一要求鉴权（JWT 或 X-API-Key）
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/urls", "/api/logs/**", "/api/account/**", "/api/stats/overview");
    }
}
