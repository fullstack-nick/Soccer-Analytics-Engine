package com.example.sportsanalytics.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchEvent(
        String providerEventId,
        String matchId,
        MatchEventType type,
        int minute,
        Integer stoppageTime,
        TeamSide teamSide,
        List<String> playerIds,
        Integer x,
        Integer y,
        Integer destinationX,
        Integer destinationY,
        Double xgValue,
        String outcome,
        UUID rawPayloadId,
        Instant occurredAt,
        Instant receivedAt
) {
    public MatchEvent {
        matchId = requireText(matchId, "matchId");
        type = Objects.requireNonNull(type, "type is required");
        teamSide = Objects.requireNonNullElse(teamSide, TeamSide.UNKNOWN);
        playerIds = List.copyOf(Objects.requireNonNullElse(playerIds, List.of()));
        if (minute < 0) {
            throw new IllegalArgumentException("minute must be greater than or equal to 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
