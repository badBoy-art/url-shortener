package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urlshortener.entity.ShortLink;

import java.time.LocalDateTime;

public record LinkResponse(
        String shortCode,
        String shortUrl,
        String destUrl,
        String description,
        boolean expired,
        Integer status,
        Integer openType,
        Long clickCount,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expiredAt
) {

    public static LinkResponse from(ShortLink link, String baseUrl) {
        return new LinkResponse(
                link.getShortCode(),
                baseUrl + "/" + link.getShortCode(),
                link.getDestUrl(),
                link.getDescription(),
                link.isExpired(LocalDateTime.now()),
                link.getStatus() == null ? 1 : link.getStatus(),
                link.getOpenType() == null ? 0 : link.getOpenType(),
                link.getClickCount() == null ? 0L : link.getClickCount(),
                link.getCreatedAt(),
                link.getExpiredAt()
        );
    }
}
