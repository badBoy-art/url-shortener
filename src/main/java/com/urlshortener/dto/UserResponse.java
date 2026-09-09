package com.urlshortener.dto;

import com.urlshortener.entity.AdminUser;

import java.time.LocalDateTime;

public record UserResponse(Long id, String account, boolean enabled, LocalDateTime createdAt) {

    public static UserResponse from(AdminUser user) {
        return new UserResponse(user.getId(), user.getAccount(),
                user.getIsEnable() == null || user.getIsEnable() == 1, user.getCreatedAt());
    }
}
