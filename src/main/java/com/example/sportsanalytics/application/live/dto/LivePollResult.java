package com.example.sportsanalytics.application.live.dto;

public record LivePollResult(
        int registeredMatches,
        int processedMatches,
        int eventsInserted,
        int eventsUpdated,
        int matchesEnded,
        int alertsCreated
) {
}
