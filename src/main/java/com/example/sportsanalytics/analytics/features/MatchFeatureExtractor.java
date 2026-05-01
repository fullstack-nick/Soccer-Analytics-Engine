package com.example.sportsanalytics.analytics.features;

import com.example.sportsanalytics.analytics.state.EventSourcedMatchState;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.Probability;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MatchFeatureExtractor {
    private final ObjectMapper objectMapper;

    public MatchFeatureExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FeatureExtractionResult extract(
            MatchEntity match,
            EventSourcedMatchState state,
            FeatureSourceContext context
    ) {
        FeatureAvailability availability = new FeatureAvailability();
        ObjectNode features = objectMapper.createObjectNode();

        put(features, availability, "scoreDifference", state.homeScore() - state.awayScore());
        int timeRemainingMinutes = Math.max(0, 90 - state.minute());
        put(features, availability, "timeRemainingMinutes", timeRemainingMinutes);
        put(features, availability, "timeRemainingRatio", timeRemainingMinutes / 90.0);
        put(features, availability, "homeAdvantage", 1.0);

        putOptional(features, availability, "teamStrengthDelta", context.providerContext().teamStrengthDelta());
        putOptional(features, availability, "lineupAdjustment", lineupAdjustment(context.lineups()));
        put(features, availability, "redCardAdjustment", (state.awayRedCards() - state.homeRedCards()) * 0.25);
        putOptional(features, availability, "xgDelta", xgDelta(state.eventsSoFar()));
        putOptional(features, availability, "shotPressureDelta", shotPressureDelta(state.eventsSoFar(), state.minute()));
        putOptional(features, availability, "shotLocationQualityDelta", shotLocationQualityDelta(state.eventsSoFar(), state.minute()));
        putOptional(features, availability, "fieldTilt", fieldTilt(state.eventsSoFar(), state.minute()));
        putOptional(features, availability, "possessionPressureDelta", possessionPressureDelta(state.eventsSoFar(), state.minute()));
        putOptional(features, availability, "momentumTrend", momentumTrend(context.momentum(), state.minute()));
        putProviderProbability(features, availability, context.providerContext().providerProbability());

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("matchId", match.getId().toString());
        meta.put("providerMatchId", match.getProviderMatchId());
        meta.put("eventId", state.event() == null ? null : state.event().getId().toString());
        meta.put("minute", state.minute());
        features.set("metadata", meta);

        ObjectNode availabilityJson = objectMapper.createObjectNode();
        availabilityJson.put("coverageMode", context.coverageMode().name());
        availabilityJson.put("featureSetVersion", FeatureExtractionResult.VERSION);
        availabilityJson.set("availableFeatures", objectMapper.valueToTree(availability.available));
        availabilityJson.set("missingFeatures", objectMapper.valueToTree(availability.missing));

        return new FeatureExtractionResult(
                features,
                availabilityJson,
                context.coverageMode(),
                FeatureExtractionResult.VERSION
        );
    }

    private void put(ObjectNode target, FeatureAvailability availability, String name, int value) {
        target.put(name, value);
        availability.available.add(name);
    }

    private void put(ObjectNode target, FeatureAvailability availability, String name, double value) {
        target.put(name, value);
        availability.available.add(name);
    }

    private void putOptional(ObjectNode target, FeatureAvailability availability, String name, Double value) {
        if (value == null || !Double.isFinite(value)) {
            target.putNull(name);
            availability.missing.add(name);
            return;
        }
        target.put(name, value);
        availability.available.add(name);
    }

    private void putProviderProbability(ObjectNode target, FeatureAvailability availability, Probability probability) {
        if (probability == null) {
            target.putNull("providerProbability");
            availability.missing.add("providerProbability");
            return;
        }
        ObjectNode probabilityNode = objectMapper.createObjectNode();
        probabilityNode.put("homeWin", probability.homeWin());
        probabilityNode.put("draw", probability.draw());
        probabilityNode.put("awayWin", probability.awayWin());
        target.set("providerProbability", probabilityNode);
        availability.available.add("providerProbability");
    }

    private Double lineupAdjustment(JsonNode lineups) {
        if (lineups == null || lineups.isMissingNode() || lineups.isNull()) {
            return null;
        }
        Integer homeStarters = null;
        Integer awayStarters = null;
        boolean formationsAvailable = false;
        JsonNode competitors = lineups.path("lineups").path("competitors");
        if (competitors.isMissingNode()) {
            competitors = lineups.path("lineups").path("competitor");
        }
        for (JsonNode competitor : jsonArray(competitors)) {
            String qualifier = competitor.path("qualifier").asText("").toLowerCase(Locale.ROOT);
            int starters = starterCount(competitor.path("players"));
            if (!competitor.path("formation").asText("").isBlank()) {
                formationsAvailable = true;
            }
            if ("home".equals(qualifier)) {
                homeStarters = starters;
            } else if ("away".equals(qualifier)) {
                awayStarters = starters;
            }
        }
        if (homeStarters == null || awayStarters == null || !formationsAvailable) {
            return null;
        }
        return (homeStarters - awayStarters) * 0.02;
    }

    private int starterCount(JsonNode playersNode) {
        if (!playersNode.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode player : playersNode) {
            if (player.path("starter").asBoolean(false)) {
                count++;
            }
        }
        return count;
    }

    private Double xgDelta(List<MatchEventEntity> events) {
        double home = 0.0;
        double away = 0.0;
        boolean available = false;
        for (MatchEventEntity event : events) {
            if (event.getXgValue() == null) {
                continue;
            }
            available = true;
            if (event.getTeamSide() == TeamSide.HOME) {
                home += event.getXgValue();
            } else if (event.getTeamSide() == TeamSide.AWAY) {
                away += event.getXgValue();
            }
        }
        return available ? home - away : null;
    }

    private Double shotPressureDelta(List<MatchEventEntity> events, int minute) {
        WindowTotals totals = new WindowTotals();
        for (MatchEventEntity event : recentWindow(events, minute)) {
            if (!isPressureShot(event)) {
                continue;
            }
            double value = 1.0 + ((event.getXgValue() == null ? 0.0 : event.getXgValue()) * 3.0);
            totals.add(event.getTeamSide(), value);
        }
        return totals.available() ? totals.home - totals.away : null;
    }

    private Double shotLocationQualityDelta(List<MatchEventEntity> events, int minute) {
        WindowTotals totals = new WindowTotals();
        for (MatchEventEntity event : recentWindow(events, minute)) {
            if (!isPressureShot(event)) {
                continue;
            }
            Double quality = event.getXgValue() == null ? coordinateQuality(event) : event.getXgValue();
            if (quality != null) {
                totals.add(event.getTeamSide(), quality);
            }
        }
        return totals.available() ? totals.home - totals.away : null;
    }

    private Double fieldTilt(List<MatchEventEntity> events, int minute) {
        WindowTotals totals = new WindowTotals();
        for (MatchEventEntity event : recentWindow(events, minute)) {
            if (event.getX() == null || event.getTeamSide() == TeamSide.UNKNOWN) {
                continue;
            }
            boolean homeFinalThird = event.getTeamSide() == TeamSide.HOME && event.getX() >= 67;
            boolean awayFinalThird = event.getTeamSide() == TeamSide.AWAY && event.getX() <= 33;
            if (isAttackingPressureAction(event) && (homeFinalThird || awayFinalThird)) {
                totals.add(event.getTeamSide(), 1.0);
            }
        }
        return totals.ratioDelta();
    }

    private Double possessionPressureDelta(List<MatchEventEntity> events, int minute) {
        WindowTotals totals = new WindowTotals();
        for (MatchEventEntity event : recentWindow(events, minute)) {
            if (isAttackingPressureAction(event)) {
                totals.add(event.getTeamSide(), 1.0);
            }
        }
        return totals.ratioDelta();
    }

    private Double momentumTrend(JsonNode momentum, int minute) {
        List<JsonNode> values = jsonArray(momentum == null ? null : momentum.path("momentums")).stream()
                .filter(node -> node.path("match_time").asInt(999) <= minute)
                .sorted(Comparator.comparingInt(node -> node.path("match_time").asInt(0)))
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        JsonNode latest = values.get(values.size() - 1);
        JsonNode baseline = values.stream()
                .filter(node -> node.path("match_time").asInt(0) <= Math.max(0, minute - 10))
                .reduce((first, second) -> second)
                .orElse(values.get(0));
        return latest.path("value").asDouble(0.0) - baseline.path("value").asDouble(0.0);
    }

    private List<MatchEventEntity> recentWindow(List<MatchEventEntity> events, int minute) {
        int start = Math.max(0, minute - 10);
        return events.stream()
                .filter(event -> event.getOccurredAtMinute() >= start && event.getOccurredAtMinute() <= minute)
                .toList();
    }

    private boolean isPressureShot(MatchEventEntity event) {
        String providerType = event.getProviderEventType() == null ? "" : event.getProviderEventType().toLowerCase(Locale.ROOT);
        return event.getEventType() == MatchEventType.SHOT
                || event.getEventType() == MatchEventType.GOAL
                || event.getEventType() == MatchEventType.PENALTY
                || event.getEventType() == MatchEventType.OFFSIDE
                || isPositiveSetPiece(event)
                || providerType.contains("possible_goal");
    }

    private boolean isAttackingPressureAction(MatchEventEntity event) {
        return event.getEventType() == MatchEventType.PASS
                || event.getEventType() == MatchEventType.SHOT
                || event.getEventType() == MatchEventType.GOAL
                || event.getEventType() == MatchEventType.PENALTY
                || event.getEventType() == MatchEventType.OFFSIDE
                || isPositiveSetPiece(event);
    }

    private boolean isPositiveSetPiece(MatchEventEntity event) {
        String providerType = event.getProviderEventType() == null ? "" : event.getProviderEventType().toLowerCase(Locale.ROOT);
        if (event.getEventType() != MatchEventType.SET_PIECE) {
            return false;
        }
        if (providerType.equals("corner_kick")) {
            return true;
        }
        return providerType.equals("throw_in") && isFinalThirdForSide(event);
    }

    private boolean isFinalThirdForSide(MatchEventEntity event) {
        if (event.getX() == null) {
            return false;
        }
        return (event.getTeamSide() == TeamSide.HOME && event.getX() >= 67)
                || (event.getTeamSide() == TeamSide.AWAY && event.getX() <= 33);
    }

    private Double coordinateQuality(MatchEventEntity event) {
        if (event.getX() == null) {
            return null;
        }
        double x = Math.max(0, Math.min(100, event.getX())) / 100.0;
        if (event.getTeamSide() == TeamSide.HOME) {
            return x;
        }
        if (event.getTeamSide() == TeamSide.AWAY) {
            return 1.0 - x;
        }
        return null;
    }

    private List<JsonNode> jsonArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(values::add);
            return values;
        }
        return List.of(node);
    }

    private static final class FeatureAvailability {
        private final List<String> available = new ArrayList<>();
        private final List<String> missing = new ArrayList<>();
    }

    private static final class WindowTotals {
        private double home;
        private double away;
        private int count;

        private void add(TeamSide side, double value) {
            if (side == TeamSide.HOME) {
                home += value;
                count++;
            } else if (side == TeamSide.AWAY) {
                away += value;
                count++;
            }
        }

        private boolean available() {
            return count > 0;
        }

        private Double ratioDelta() {
            double total = home + away;
            return total == 0.0 ? null : (home - away) / total;
        }
    }
}
