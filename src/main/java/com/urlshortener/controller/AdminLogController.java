package com.urlshortener.controller;

import com.urlshortener.common.ApiResponse;
import com.urlshortener.entity.AccessLog;
import com.urlshortener.service.AdminLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "管理端访问日志", description = "访问日志查询与 Excel 导出（需 JWT 或 X-API-Key）")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    @Operation(summary = "访问日志查询", description = "按短码 + 时间范围过滤，分页返回，含总条数与独立 IP 数")
    @GetMapping
    public ApiResponse<Map<String, Object>> query(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = adminLogService.queryLogs(code, start, end, page, size);
        List<AccessLog> logs = result.getRecords();
        return ApiResponse.ok(Map.of(
                "page", page,
                "size", size,
                "total", result.getTotal(),
                "uniqueIpCount", adminLogService.countDistinctIps(code, start, end),
                "list", logs));
    }

    @Operation(summary = "访问日志导出 Excel", description = "按短码 + 时间范围过滤导出 .xlsx（行数受 app.admin.export-max-rows 限制）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        byte[] content = adminLogService.exportToExcel(code, start, end);
        String filename = "访问日志_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
