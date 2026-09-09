package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 负载均衡探活：/health 进程存活，/ready 依赖就绪（数据库可连通）。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok();
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Void>> ready() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok());
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(ApiResponse.error(503, "依赖组件不可用"));
        }
    }
}
