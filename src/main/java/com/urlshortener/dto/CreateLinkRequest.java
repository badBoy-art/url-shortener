package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "创建短链请求")
public record CreateLinkRequest(
        @Schema(description = "主目标长地址（http/https）；仅提供 destinations 时可留空，此时取 destinations 中第一个作为主目标（用于短码生成与无 X-Dest-Label 请求头时的默认跳转）",
                example = "https://www.example.com/very/long/path?a=1")
        @Size(max = 2048, message = "destUrl 长度不能超过 2048")
        String destUrl,

        @Schema(description = "多目标地址列表：每个目标一个 label 标识，访问时通过 X-Dest-Label 请求头选择；与 destUrl 至少提供一个")
        @Valid
        @Size(max = 20, message = "destinations 最多 20 个")
        List<DestInfo> destinations,

        @Schema(description = "自定义短码（4-16 位 Base58 字符），留空则自动生成", example = "mycode")
        String shortCode,

        @Schema(description = "访问密码，设置后访问需先输入密码")
        @Size(max = 64, message = "密码长度不能超过 64")
        String password,

        @Schema(description = "备注")
        @Size(max = 512, message = "备注长度不能超过 512")
        String description,

        @Schema(description = "打开方式：0 全部 1 微信 2 钉钉 3 iPhone 4 Android 5 iPad 6 Safari 7 Chrome 8 Firefox，默认 0",
                example = "0", defaultValue = "0")
        @Min(value = 0, message = "openType 取值需在 0-8 之间")
        @Max(value = 8, message = "openType 取值需在 0-8 之间")
        Integer openType,

        @Schema(description = "过期时间（ISO 格式），留空永不过期", example = "2026-12-31T23:59:59")
        LocalDateTime expiredAt
) {
}
