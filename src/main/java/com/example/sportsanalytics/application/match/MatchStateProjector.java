package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.sportradar.mapping.CoverageDetectionResult;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MatchStateProjector {
    private final ObjectMapper objectMapper;

    public MatchStateProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MatchStateProjection project(
            MatchMetadata metadata,
            CoverageDetectionResult coverage,
            List<NormalizedTimelineEvent> events,
            JsonNode lineups,
            JsonNode momentum,
            JsonNode extendedSummary,
            JsonNode seasonInfo,
            Map<String, UUID> payloadIds
    ) {
        int minute = events.stream().map(NormalizedTimelineEvent::minute).max(Integer::compareTo).orElse(0);
        int homeScore = metadata.homeScore();
        int awayScore = metadata.awayScore();
        for (NormalizedTimelineEvent event : events) {
            if (event.homeScoreAfter() != null) {
                homeScore = event.homeScoreAfter();
            }
            if (event.awayScoreAfter() != null) {
                awayScore = event.awayScoreAfter();
            }
        }
        int homeRedCards = redCards(events, TeamSide.HOME);
        int awayRedCards = redCards(events, TeamSide.AWAY);

        ObjectNode state = objectMapper.createObjectNode();
        state.put("providerMatchId", metadata.providerMatchId());
        state.put("status", metadata.status());
        state.put("matchStatus", metadata.matchStatus());
        state.put("coverageMode", coverage.mode().name());
        state.set("coverageReasons", objectMapper.valueToTree(coverage.reasons()));
        state.set("teams", teams(metadata));
        state.set("score", score(homeScore, awayScore));
        state.set("clock", clock(minute));
        state.set("redCards", redCards(homeRedCards, awayRedCards));
        state.set("lineups", lineupSummary(lineups));
        state.set("latestMomentum", latestMomentum(momentum));
        state.set("accumulatedStats", accumulatedStats(extendedSummary));
        state.set("seasonCoverage", seasonCoverage(seasonInfo));
        state.set("sourcePayloadIds", objectMapper.valueToTree(payloadIds));

        return new MatchStateProjection(minute, homeScore, awayScore, homeRedCards, awayRedCards, state);
    }

    private int redCards(List<NormalizedTimelineEvent> events, TeamSide side) {
        return (int) events.stream()
                .filter(event -> event.eventType() == MatchEventType.CARD)
                .filter(event -> event.teamSide() == side)
                .filter(event -> {
                    String outcome = event.outcome();
                    return outcome != null && outcome.toLowerCase().contains("red");
                })
                .count();
    }

    private ObjectNode teams(MatchMetadata metadata) {
        ObjectNode teams = objectMapper.createObjectNode();
        ObjectNode home = objectMapper.createObjectNode();
        home.put("id", metadata.homeTeam().id());
        home.put("name", metadata.homeTeam().name());
        ObjectNode away = objectMapper.createObjectNode();
        away.put("id", metadata.awayTeam().id());
        away.put("name", metadata.awayTeam().name());
        teams.set("home", home);
        teams.set("away", away);
        return teams;
    }

    private ObjectNode score(int homeScore, int awayScore) {
        ObjectNode score = objectMapper.createObjectNode();
        score.put("home", homeScore);
        score.put("away", awayScore);
        return score;
    }

    private ObjectNode clock(int minute) {
        ObjectNode clock = objectMapper.createObjectNode();
        clock.put("minute", minute);
        return clock;
    }

    private ObjectNode redCards(int homeRedCards, int awayRedCards) {
        ObjectNode redCards = objectMapper.createObjectNode();
        redCards.put("home", homeRedCards);
        redCards.put("away", awayRedCards);
        return redCards;
    }

    private JsonNode lineupSummary(JsonNode lineups) {
        ObjectNode summary = objectMapper.createObjectNode();
        if (lineups == null || lineups.isMissingNode() || lineups.isNull()) {
            summary.put("available", false);
            return summary;
        }
        summary.put("available", true);
        ArrayNode competitors = objectMapper.createArrayNode();
        JsonNode lineupCompetitors = lineups.path("lineups").path("competitors");
        if (lineupCompetitors.isMissingNode()) {
            lineupCompetitors = lineups.path("lineups").path("competitor");
        }
        if (lineupCompetitors.isArray()) {
            for (JsonNode competitor : lineupCompetitors) {
                ObjectNode competitorSummary = objectMapper.createObjectNode();
                competitorSummary.put("id", competitor.path("id").asText(null));
                competitorSummary.put("name", competitor.path("name").asText(null));
                competitorSummary.put("qualifier", competitor.path("qualifier").asText(null));
                competitorSummary.put("formation", competitor.path("formation").asText(null));
                competitorSummary.put("starterCount", starterCount(competitor.path("players")));
                competitors.add(competitorSummary);
            }
        }
        summary.set("competitors", competitors);
        return summary;
    }

    private int starterCount(JsonNode playersNode) {
        int starters = 0;
        if (!playersNode.isArray()) {
            return starters;
        }
        for (JsonNode player : playersNode) {
            if (player.path("starter").asBoolean(false)) {
                starters++;
            }
        }
        return starters;
    }

    private JsonNode latestMomentum(JsonNode momentum) {
        ObjectNode empty = objectMapper.createObjectNode();
        empty.put("available", false);
        if (momentum == null || momentum.isMissingNode() || momentum.isNull()) {
            return empty;
        }
        return jsonArray(momentum.path("momentums")).stream()
                .max(Comparator.comparingInt(node -> node.path("match_time").asInt(0)))
                .map(node -> {
                    ObjectNode latest = objectMapper.createObjectNode();
                    latest.put("available", true);
                    latest.put("matchTime", node.path("match_time").asInt(0));
                    latest.put("value", node.path("value").asInt(0));
                    return latest;
                })
                .orElse(empty);
    }

    private JsonNode accumulatedStats(JsonNode extendedSummary) {
        if (extendedSummary == null || extendedSummary.isMissingNode() || extendedSummary.isNull()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("available", false);
            return empty;
        }
        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("available", true);
        stats.set("statistics", extendedSummary.path("statistics").isMissingNode()
                ? objectMapper.createObjectNode()
                : extendedSummary.path("statistics"));
        return stats;
    }

    private JsonNode seasonCoverage(JsonNode seasonInfo) {
        if (seasonInfo == null || seasonInfo.isMissingNode() || seasonInfo.isNull()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("available", false);
            return empty;
        }
        ObjectNode coverage = objectMapper.createObjectNode();
        coverage.put("available", true);
        coverage.set("coverage", seasonInfo.path("coverage"));
        return coverage;
    }

    private List<JsonNode> jsonArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
    }
}
