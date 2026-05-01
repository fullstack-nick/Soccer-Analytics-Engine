package com.example.sportsanalytics.analytics.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SeasonScheduleParser {
    public List<String> finishedSportEventIds(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        JsonNode schedules = root.path("schedules");
        if (!schedules.isArray()) {
            schedules = root.path("schedule");
        }
        if (!schedules.isArray()) {
            return List.of();
        }
        for (JsonNode schedule : schedules) {
            String sportEventId = schedule.path("sport_event").path("id").asText(null);
            if (sportEventId == null || sportEventId.isBlank()) {
                continue;
            }
            if (isFinished(schedule.path("sport_event_status"))) {
                ids.add(sportEventId);
            }
        }
        return List.copyOf(ids);
    }

    private boolean isFinished(JsonNode status) {
        String text = ((status.path("status").asText("") + " " + status.path("match_status").asText("")))
                .toLowerCase(Locale.ROOT);
        if (text.contains("not_started") || text.contains("not started") || text.contains("scheduled")
                || text.contains("postponed") || text.contains("cancel") || text.contains("abandoned")
                || text.contains("interrupted") || text.contains("live")) {
            return false;
        }
        return text.contains("closed") || text.contains("complete") || text.contains("finished")
                || text.contains("ended") || text.contains("played");
    }
}
