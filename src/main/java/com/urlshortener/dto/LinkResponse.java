package com.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urlshortener.entity.ShortLink;

import java.time.LocalDateTime;
import java.util.List;

public record LinkResponse(
        String shortCode,
        String shortUrl,
        String destUrl,
        List<DestInfo> destinations,
        String description,
        boolean expired,
        Integer status,
        Integer openType,
        Long clickCount,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expiredAt
) {

    public static LinkResponse from(ShortLink link, String baseUrl) {
        return from(link, baseUrl, null);
    }

    public static LinkResponse from(ShortLink link, String baseUrl, String destUrlOverride) {
        List<DestInfo> dests = link.getDestinations() == null ? null : link.getDestinations()
                .entrySet().stream()
                .map(e -> new DestInfo(e.getKey(), e.getValue()))
                .toList();
        return new LinkResponse(
                link.getShortCode(),
                baseUrl + "/" + link.getShortCode(),
                destUrlOverride != null ? destUrlOverride : link.getDestUrl(),
                dests,
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
