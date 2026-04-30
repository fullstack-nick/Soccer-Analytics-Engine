package com.example.sportsanalytics.application.match;

import com.fasterxml.jackson.databind.JsonNode;

public record MatchStateProjection(
        int minute,
        int homeScore,
        int awayScore,
        int homeRedCards,
        int awayRedCards,
        JsonNode stateJson
) {
}
