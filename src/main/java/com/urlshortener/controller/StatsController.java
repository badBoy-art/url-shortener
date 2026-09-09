package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.dto.OverviewResponse;
import com.urlshortener.dto.StatsResponse;
import com.urlshortener.ratelimit.ApiKeyRequired;
import com.urlshortener.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "访问统计")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/api/url/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}/stats")
    @Operation(summary = "单链统计", description = "总点击、今日 PV/UV、今日 24 小时分时 PV")
    public ApiResponse<StatsResponse> linkStats(@PathVariable String code) {
        return ApiResponse.ok(statsService.linkStats(code));
    }

    @GetMapping("/api/stats/overview")
    @ApiKeyRequired
    @Operation(summary = "全局统计（需 X-API-Key 请求头）")
    public ApiResponse<OverviewResponse> overview() {
        return ApiResponse.ok(statsService.overview());
    }
}
