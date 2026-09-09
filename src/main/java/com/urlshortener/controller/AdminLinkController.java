package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.LinkResponse;
import com.urlshortener.service.AdminLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端短链管理", description = "短链列表/搜索（需 JWT 或 X-API-Key）")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class AdminLinkController {

    private final AdminLinkService adminLinkService;
    private final AppProperties props;

    @Operation(summary = "短链列表", description = "分页；keyword 对短码/目标地址/备注模糊搜索")
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        var result = adminLinkService.listLinks(keyword, page, size);
        List<LinkResponse> list = result.getRecords().stream()
                .map(link -> LinkResponse.from(link, props.baseUrl()))
                .toList();
        return ApiResponse.ok(Map.of(
                "page", page,
                "size", size,
                "total", result.getTotal(),
                "list", list));
    }
}
