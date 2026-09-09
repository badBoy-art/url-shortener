package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        String password) {
}
