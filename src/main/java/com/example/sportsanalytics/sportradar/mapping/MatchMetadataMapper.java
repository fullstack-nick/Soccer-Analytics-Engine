package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.TeamSide;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MatchMetadataMapper {
    public MatchMetadata fromSummary(JsonNode summary) {
        JsonNode sportEvent = summary.path("sport_event");
        JsonNode status = summary.path("sport_event_status");

        String providerMatchId = required(JsonNodes.text(sportEvent, "id"), "sport_event.id");
        String seasonId = orUnknown(JsonNodes.firstTextAt(
                sportEvent,
                new String[]{"sport_event_context", "season", "id"},
                new String[]{"season", "id"}
        ));
        String competitionId = orUnknown(JsonNodes.firstTextAt(
                sportEvent,
                new String[]{"sport_event_context", "competition", "id"},
                new String[]{"competition", "id"}
        ));

        List<TeamMetadata> teams = JsonNodes.array(sportEvent, "competitors").stream()
                .map(this::team)
                .toList();
        TeamMetadata home = teams.stream()
                .filter(team -> team.side() == TeamSide.HOME)
                .findFirst()
                .orElseGet(() -> teams.isEmpty() ? null : new TeamMetadata(teams.get(0).id(), teams.get(0).name(), TeamSide.HOME));
        TeamMetadata away = teams.stream()
                .filter(team -> team.side() == TeamSide.AWAY)
                .findFirst()
                .orElseGet(() -> teams.size() < 2 ? null : new TeamMetadata(teams.get(1).id(), teams.get(1).name(), TeamSide.AWAY));

        if (home == null || away == null) {
            throw new SportradarPayloadMappingException("summary payload does not contain home and away competitors");
        }

        return new MatchMetadata(
                providerMatchId,
                seasonId,
                competitionId,
                home,
                away,
                JsonNodes.instant(sportEvent, "start_time"),
                integerOrZero(status, "home_score"),
                integerOrZero(status, "away_score"),
                JsonNodes.text(status, "status"),
                JsonNodes.text(status, "match_status")
        );
    }

    private TeamMetadata team(JsonNode competitor) {
        return new TeamMetadata(
                required(JsonNodes.text(competitor, "id"), "competitor.id"),
                required(JsonNodes.text(competitor, "name"), "competitor.name"),
                switch (String.valueOf(JsonNodes.text(competitor, "qualifier")).toLowerCase()) {
                    case "home" -> TeamSide.HOME;
                    case "away" -> TeamSide.AWAY;
                    default -> TeamSide.UNKNOWN;
                }
        );
    }

    private static int integerOrZero(JsonNode node, String field) {
        Integer value = JsonNodes.integer(node, field);
        return value == null ? 0 : value;
    }

    private static String orUnknown(String value) {
        return value == null ? "UNKNOWN" : value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SportradarPayloadMappingException("summary payload is missing " + field);
        }
        return value;
    }
}
