package com.urlshortener.dto;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "enable 不能为空")
        Boolean enable) {
}
