package com.urlshortener.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "多目标地址条目：label 为标识（访问时通过 X-Dest-Label 请求头选择），destUrl 为目标地址")
public record DestInfo(
        @Schema(description = "目标标识（请求头 X-Dest-Label 的取值）", example = "pc")
        @NotBlank(message = "label 不能为空")
        @Size(max = 64, message = "label 长度不能超过 64")
        String label,

        @Schema(description = "目标长地址（http/https）", example = "https://www.example.com/pc")
        @NotBlank(message = "destUrl 不能为空")
        @Size(max = 2048, message = "destUrl 长度不能超过 2048")
        String destUrl
) {
}
