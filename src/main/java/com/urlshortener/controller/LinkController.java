package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.ratelimit.ApiKeyRequired;
import com.urlshortener.ratelimit.RateLimit;
import com.urlshortener.service.ShortLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
@Tag(name = "短链管理")
public class LinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping
    @RateLimit
    @Operation(summary = "创建短链", description = "同一 destUrl 恒生成同一短码；可自定义短码、设置访问密码、备注与过期时间")
    public ApiResponse<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        return ApiResponse.ok(shortLinkService.create(request));
    }

    @GetMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}")
    @Operation(summary = "查询短链信息")
    public ApiResponse<LinkResponse> info(@PathVariable String code) {
        return ApiResponse.ok(shortLinkService.getInfo(code));
    }

    @DeleteMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}")
    @ApiKeyRequired
    @Operation(summary = "删除短链（需 X-API-Key 请求头）")
    public ApiResponse<Void> delete(@PathVariable String code) {
        shortLinkService.delete(code);
        return ApiResponse.ok();
    }
}
