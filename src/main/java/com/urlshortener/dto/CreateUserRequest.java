package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "账号不能为空")
        @Size(min = 5, max = 64, message = "账号长度需在 5-64 之间")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        String password) {
}
