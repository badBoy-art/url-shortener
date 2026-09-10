package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.common.WebUtil;
import com.urlshortener.dto.CreateLinkRequest;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.dto.StatusUpdateRequest;
import com.urlshortener.ratelimit.ApiKeyRequired;
import com.urlshortener.ratelimit.RateLimit;
import com.urlshortener.service.AdminLinkService;
import com.urlshortener.service.ShortLinkService;
import com.urlshortener.service.UserAgentAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/url")
@RequiredArgsConstructor
@Tag(name = "短链管理")
public class LinkController {

    private final ShortLinkService shortLinkService;
    private final AdminLinkService adminLinkService;
    private final UserAgentAnalysisService uaService;

    @PostMapping
    @RateLimit
    @Operation(summary = "创建短链", description = "同一 destUrl 恒生成同一短码；支持 destinations 多目标地址（每个目标一个 label，访问时按 X-Client-Type/X-Platform 请求头与 User-Agent 设备类型选择）；"
            + "可自定义短码、设置访问密码、备注与过期时间")
    public ApiResponse<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        return ApiResponse.ok(shortLinkService.create(request));
    }

    @GetMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}")
    @Operation(summary = "查询短链信息", description = "多目标短链按 X-Client-Type/X-Platform 请求头与 User-Agent 设备类型（pc/mobile/tablet）解析 destUrl（无匹配返回主目标）")
    public ApiResponse<LinkResponse> info(@PathVariable String code, HttpServletRequest request) {
        return ApiResponse.ok(shortLinkService.getInfo(code, WebUtil.destLabels(request, uaService)));
    }

    @DeleteMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}")
    @ApiKeyRequired
    @Operation(summary = "删除短链（需 X-API-Key 请求头）")
    public ApiResponse<Void> delete(@PathVariable String code) {
        shortLinkService.delete(code);
        return ApiResponse.ok();
    }

    @PutMapping("/{code:[1-9A-HJ-NP-Za-km-z]{4,16}}/change_state")
    @ApiKeyRequired
    @Operation(summary = "启用/停用短链", description = "停用后访问返回 410，缓存同步失效")
    public ApiResponse<LinkResponse> changeState(@PathVariable String code,
                                                  @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok(adminLinkService.setStatus(code, request.enable()));
    }
}
