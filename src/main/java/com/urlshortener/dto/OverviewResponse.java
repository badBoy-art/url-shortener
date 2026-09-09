package com.urlshortener.dto;

import java.util.List;

public record OverviewResponse(
        long totalLinks,
        long totalClicks,
        long todayPv,
        long todayUv,
        long yesterdayPv,
        long yesterdayUv,
        long last7DaysPv,
        long last7DaysUv,
        long monthlyPv,
        long monthlyUv,
        List<TopLink> topLinks
) {

    public record TopLink(String shortCode, String destUrl, long clickCount) {
    }
}
