package com.example.sportsanalytics.analytics.state;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventSourcedMatchStateProjector {
    private final ObjectMapper objectMapper;

    public EventSourcedMatchStateProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<EventSourcedMatchState> project(
            MatchEntity match,
            MatchMetadata metadata,
            CoverageMode coverageMode,
            List<String> coverageReasons,
            List<MatchEventEntity> orderedEvents,
            JsonNode lineups,
            JsonNode momentum,
            JsonNode seasonInfo,
            Map<String, UUID> payloadIds
    ) {
        StateAccumulator accumulator = new StateAccumulator(metadata);
        List<EventSourcedMatchState> snapshots = new ArrayList<>();
        List<MatchEventEntity> eventsSoFar = new ArrayList<>();

        if (orderedEvents.isEmpty()) {
            int minute = inferMinute(metadata);
            ObjectNode state = stateJson(match, metadata, coverageMode, coverageReasons, minute, accumulator,
                    lineups, momentum, seasonInfo, payloadIds);
            snapshots.add(new EventSourcedMatchState(
                    1,
                    null,
                    minute,
                    metadata.homeScore(),
                    metadata.awayScore(),
                    0,
                    0,
                    state,
                    List.of()
            ));
            return snapshots;
        }

        long version = 1;
        for (MatchEventEntity event : orderedEvents) {
            accumulator.apply(event);
            eventsSoFar.add(event);
            ObjectNode state = stateJson(match, metadata, coverageMode, coverageReasons, accumulator.minute,
                    accumulator, lineups, momentum, seasonInfo, payloadIds);
            snapshots.add(new EventSourcedMatchState(
                    version++,
                    event,
                    accumulator.minute,
                    accumulator.homeScore,
                    accumulator.awayScore,
                    accumulator.homeRedCards,
                    accumulator.awayRedCards,
                    state,
                    eventsSoFar
            ));
        }
        return snapshots;
    }

    private ObjectNode stateJson(
            MatchEntity match,
            MatchMetadata metadata,
            CoverageMode coverageMode,
            List<String> coverageReasons,
            int minute,
            StateAccumulator accumulator,
            JsonNode lineups,
            JsonNode momentum,
            JsonNode seasonInfo,
            Map<String, UUID> payloadIds
    ) {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("providerMatchId", match.getProviderMatchId());
        state.put("status", accumulator.status);
        state.put("matchStatus", accumulator.matchStatus);
        state.put("coverageMode", coverageMode.name());
        state.set("coverageReasons", objectMapper.valueToTree(coverageReasons));
        state.set("teams", teams(metadata));
        state.set("score", score(accumulator.homeScore, accumulator.awayScore));
        state.set("clock", clock(minute));
        state.set("cards", cards(accumulator));
        state.set("redCards", redCards(accumulator.homeRedCards, accumulator.awayRedCards));
        state.set("substitutions", substitutions(accumulator));
        state.set("lineups", lineupSummary(lineups));
        state.set("latestMomentum", latestMomentum(momentum, minute));
        state.set("accumulatedStats", accumulatedStats(accumulator));
        state.set("seasonCoverage", seasonCoverage(seasonInfo));
        state.set("sourcePayloadIds", objectMapper.valueToTree(payloadIds));
        return state;
    }

    private void applyCard(StateAccumulator accumulator, MatchEventEntity event) {
        TeamSide side = event.getTeamSide();
        String value = normalizedEventText(event);
        boolean yellowRed = value.contains("yellow_red") || value.contains("yellow red");
        boolean red = yellowRed || value.contains("red");
        boolean yellow = yellowRed || value.contains("yellow");
        if (side == TeamSide.HOME) {
            if (yellow) {
                accumulator.homeYellowCards++;
            }
            if (yellowRed) {
                accumulator.homeYellowRedCards++;
            }
            if (red) {
                accumulator.homeRedCards++;
            }
        } else if (side == TeamSide.AWAY) {
            if (yellow) {
                accumulator.awayYellowCards++;
            }
            if (yellowRed) {
                accumulator.awayYellowRedCards++;
            }
            if (red) {
                accumulator.awayRedCards++;
            }
        }
    }

    private String normalizedEventText(MatchEventEntity event) {
        return ((event.getProviderEventType() == null ? "" : event.getProviderEventType()) + " "
                + (event.getOutcome() == null ? "" : event.getOutcome())).toLowerCase(Locale.ROOT);
    }

    private int inferMinute(MatchMetadata metadata) {
        String combined = ((metadata.status() == null ? "" : metadata.status()) + " "
                + (metadata.matchStatus() == null ? "" : metadata.matchStatus())).toLowerCase(Locale.ROOT);
        return combined.contains("closed") || combined.contains("ended") || combined.contains("finished") ? 90 : 0;
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
        clock.put("timeRemainingMinutes", Math.max(0, 90 - minute));
        return clock;
    }

    private ObjectNode cards(StateAccumulator accumulator) {
        ObjectNode cards = objectMapper.createObjectNode();
        ObjectNode home = objectMapper.createObjectNode();
        home.put("yellow", accumulator.homeYellowCards);
        home.put("yellowRed", accumulator.homeYellowRedCards);
        home.put("red", accumulator.homeRedCards);
        ObjectNode away = objectMapper.createObjectNode();
        away.put("yellow", accumulator.awayYellowCards);
        away.put("yellowRed", accumulator.awayYellowRedCards);
        away.put("red", accumulator.awayRedCards);
        cards.set("home", home);
        cards.set("away", away);
        return cards;
    }

    private ObjectNode redCards(int homeRedCards, int awayRedCards) {
        ObjectNode redCards = objectMapper.createObjectNode();
        redCards.put("home", homeRedCards);
        redCards.put("away", awayRedCards);
        return redCards;
    }

    private ObjectNode substitutions(StateAccumulator accumulator) {
        ObjectNode substitutions = objectMapper.createObjectNode();
        substitutions.put("home", accumulator.homeSubstitutions);
        substitutions.put("away", accumulator.awaySubstitutions);
        return substitutions;
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
        if (!playersNode.isArray()) {
            return 0;
        }
        int starters = 0;
        for (JsonNode player : playersNode) {
            if (player.path("starter").asBoolean(false)) {
                starters++;
            }
        }
        return starters;
    }

    private JsonNode latestMomentum(JsonNode momentum, int minute) {
        ObjectNode empty = objectMapper.createObjectNode();
        empty.put("available", false);
        if (momentum == null || momentum.isMissingNode() || momentum.isNull()) {
            return empty;
        }
        return jsonArray(momentum.path("momentums")).stream()
                .filter(node -> node.path("match_time").asInt(999) <= minute)
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

    private JsonNode accumulatedStats(StateAccumulator accumulator) {
        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("available", true);
        stats.set("home", sideStats(
                accumulator.homeConfirmedGoals,
                accumulator.homeShots,
                accumulator.homePasses,
                accumulator.homeFouls,
                accumulator.homeCards,
                accumulator.homeSubstitutions,
                accumulator.homeXg
        ));
        stats.set("away", sideStats(
                accumulator.awayConfirmedGoals,
                accumulator.awayShots,
                accumulator.awayPasses,
                accumulator.awayFouls,
                accumulator.awayCards,
                accumulator.awaySubstitutions,
                accumulator.awayXg
        ));
        return stats;
    }

    private ObjectNode sideStats(int goals, int shots, int passes, int fouls, int cards, int substitutions, double xg) {
        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("confirmedGoals", goals);
        stats.put("shots", shots);
        stats.put("passes", passes);
        stats.put("fouls", fouls);
        stats.put("cards", cards);
        stats.put("substitutions", substitutions);
        stats.put("xg", xg);
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

    private final class StateAccumulator {
        private int minute;
        private int homeScore;
        private int awayScore;
        private int homeRedCards;
        private int awayRedCards;
        private int homeYellowCards;
        private int awayYellowCards;
        private int homeYellowRedCards;
        private int awayYellowRedCards;
        private int homeSubstitutions;
        private int awaySubstitutions;
        private int homeConfirmedGoals;
        private int awayConfirmedGoals;
        private int homeShots;
        private int awayShots;
        private int homePasses;
        private int awayPasses;
        private int homeFouls;
        private int awayFouls;
        private int homeCards;
        private int awayCards;
        private double homeXg;
        private double awayXg;
        private String status;
        private String matchStatus;

        private StateAccumulator(MatchMetadata metadata) {
            this.homeScore = metadata.homeScore();
            this.awayScore = metadata.awayScore();
            this.status = metadata.status();
            this.matchStatus = metadata.matchStatus();
        }

        private void apply(MatchEventEntity event) {
            minute = Math.max(minute, event.getOccurredAtMinute());
            if (event.getHomeScoreAfter() != null) {
                homeScore = event.getHomeScoreAfter();
            }
            if (event.getAwayScoreAfter() != null) {
                awayScore = event.getAwayScoreAfter();
            }
            if (event.isScoreChanged() && event.getEventType() == MatchEventType.GOAL) {
                if (event.getTeamSide() == TeamSide.HOME) {
                    homeConfirmedGoals++;
                } else if (event.getTeamSide() == TeamSide.AWAY) {
                    awayConfirmedGoals++;
                }
            }
            if (event.getXgValue() != null) {
                if (event.getTeamSide() == TeamSide.HOME) {
                    homeXg += event.getXgValue();
                } else if (event.getTeamSide() == TeamSide.AWAY) {
                    awayXg += event.getXgValue();
                }
            }
            switch (event.getEventType()) {
                case SHOT -> incrementSide(event.getTeamSide(), Counter.SHOT);
                case PASS -> incrementSide(event.getTeamSide(), Counter.PASS);
                case FOUL -> incrementSide(event.getTeamSide(), Counter.FOUL);
                case CARD -> {
                    incrementSide(event.getTeamSide(), Counter.CARD);
                    applyCard(this, event);
                }
                case SUBSTITUTION -> incrementSide(event.getTeamSide(), Counter.SUBSTITUTION);
                case PERIOD -> applyPeriodStatus(event);
                default -> {
                }
            }
        }

        private void applyPeriodStatus(MatchEventEntity event) {
            String value = normalizedEventText(event);
            if (value.contains("match_started")) {
                status = "live";
                matchStatus = "started";
            } else if (value.contains("ended") || value.contains("finished")) {
                status = "closed";
                matchStatus = "ended";
            } else if (!value.isBlank()) {
                matchStatus = event.getProviderEventType();
            }
        }

        private void incrementSide(TeamSide side, Counter counter) {
            if (side == TeamSide.HOME) {
                switch (counter) {
                    case SHOT -> homeShots++;
                    case PASS -> homePasses++;
                    case FOUL -> homeFouls++;
                    case CARD -> homeCards++;
                    case SUBSTITUTION -> homeSubstitutions++;
                }
            } else if (side == TeamSide.AWAY) {
                switch (counter) {
                    case SHOT -> awayShots++;
                    case PASS -> awayPasses++;
                    case FOUL -> awayFouls++;
                    case CARD -> awayCards++;
                    case SUBSTITUTION -> awaySubstitutions++;
                }
            }
        }
    }

    private enum Counter {
        SHOT,
        PASS,
        FOUL,
        CARD,
        SUBSTITUTION
    }
}
