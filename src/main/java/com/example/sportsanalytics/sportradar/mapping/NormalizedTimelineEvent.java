package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import java.util.List;
import java.util.UUID;

public record NormalizedTimelineEvent(
        String providerEventId,
        long sequence,
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
        Integer homeScore,
        Integer awayScore,
        TimelineSourceType sourceTimelineType,
        UUID rawPayloadId
) {
    public NormalizedTimelineEvent {
        playerIds = List.copyOf(playerIds == null ? List.of() : playerIds);
    }
}
