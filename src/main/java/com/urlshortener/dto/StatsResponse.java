package com.urlshortener.dto;

import java.util.List;

public record StatsResponse(
        String shortCode,
        long totalClicks,
        long todayPv,
        long todayUv,
        long yesterdayPv,
        long yesterdayUv,
        long last7DaysPv,
        long last7DaysUv,
        long monthlyPv,
        long monthlyUv,
        List<HourlyStat> hourly
) {

    public record HourlyStat(int hour, long pv) {
    }
}
