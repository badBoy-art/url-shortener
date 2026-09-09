package com.urlshortener.cache;

import com.urlshortener.entity.ShortLink;

import java.time.LocalDateTime;

/**
 * 缓存与重定向所需的链接摘要，避免把完整实体放进缓存。
 */
public record LinkInfo(
        String code,
        String destUrl,
        String passwordHash,
        String description,
        Integer status,
        Integer openType,
        Long clickCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expiredAt
) {

    public static LinkInfo from(ShortLink link) {
        return new LinkInfo(link.getShortCode(), link.getDestUrl(), link.getPasswordHash(),
                link.getDescription(), link.getStatus(), link.getOpenType(), link.getClickCount(),
                link.getCreatedAt(), link.getUpdatedAt(), link.getExpiredAt());
    }

    public ShortLink toEntity() {
        ShortLink link = new ShortLink();
        link.setShortCode(code);
        link.setDestUrl(destUrl);
        link.setPasswordHash(passwordHash);
        link.setDescription(description);
        link.setStatus(status);
        link.setOpenType(openType);
        link.setClickCount(clickCount);
        link.setCreatedAt(createdAt);
        link.setUpdatedAt(updatedAt);
        link.setExpiredAt(expiredAt);
        return link;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiredAt != null && expiredAt.isBefore(now);
    }

    public boolean isEnabled() {
        return status == null || status == 1;
    }
}
