package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "多目标地址条目：label 为标识（浏览器场景建议用 pc/mobile/tablet 靠 User-Agent 自动选择，App 场景用与 X-Client-Type/X-Platform 请求头一致的取值），destUrl 为目标地址")
public record DestInfo(
        @Schema(description = "目标标识（X-Client-Type/X-Platform 请求头取值或设备类型 pc/mobile/tablet）", example = "pc")
        @NotBlank(message = "label 不能为空")
        @Size(max = 64, message = "label 长度不能超过 64")
        String label,

        @Schema(description = "目标长地址（http/https）", example = "https://www.example.com/pc")
        @NotBlank(message = "destUrl 不能为空")
        @Size(max = 2048, message = "destUrl 长度不能超过 2048")
        String destUrl
) {
}
