package com.example.sportsanalytics.analytics.state;

import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record EventSourcedMatchState(
        long version,
        MatchEventEntity event,
        int minute,
        int homeScore,
        int awayScore,
        int homeRedCards,
        int awayRedCards,
        JsonNode stateJson,
        List<MatchEventEntity> eventsSoFar
) {
    public EventSourcedMatchState {
        eventsSoFar = List.copyOf(eventsSoFar == null ? List.of() : eventsSoFar);
    }
}
