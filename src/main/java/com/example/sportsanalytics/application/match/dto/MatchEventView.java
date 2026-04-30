package com.example.sportsanalytics.application.match.dto;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchEventView(
        UUID id,
        UUID matchId,
        String providerEventId,
        String providerEventType,
        long eventSequence,
        MatchEventType eventType,
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
        Integer homeScoreAfter,
        Integer awayScoreAfter,
        boolean scoreChanged,
        TimelineSourceType sourceTimelineType,
        Instant receivedAt
) {
    public MatchEventView {
        playerIds = List.copyOf(playerIds == null ? List.of() : playerIds);
    }
}
