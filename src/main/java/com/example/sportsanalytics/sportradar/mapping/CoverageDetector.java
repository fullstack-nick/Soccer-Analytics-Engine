package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CoverageDetector {
    public CoverageDetectionResult detect(
            JsonNode summary,
            JsonNode timeline,
            JsonNode extendedTimeline,
            JsonNode lineups,
            JsonNode momentum,
            JsonNode extendedSummary,
            JsonNode seasonInfo
    ) {
        List<String> reasons = new ArrayList<>();
        if (hasRichTimeline(extendedTimeline)) {
            reasons.add("extended timeline contains coordinate or xG event detail");
            return new CoverageDetectionResult(CoverageMode.RICH, reasons);
        }
        if (hasExtendedStats(extendedSummary)) {
            reasons.add("extended summary contains xG or pressure statistics");
            return new CoverageDetectionResult(CoverageMode.RICH, reasons);
        }
        if (JsonNodes.boolAt(seasonInfo, "coverage", "extended_play_by_play")) {
            reasons.add("season coverage advertises extended play-by-play");
            return new CoverageDetectionResult(CoverageMode.RICH, reasons);
        }
        if (hasTimeline(timeline)) {
            reasons.add("regular timeline is available");
        }
        if (hasLineups(lineups)) {
            reasons.add("lineups are available");
        }
        if (hasMomentum(momentum)) {
            reasons.add("momentum feed is available");
        }
        if (JsonNodes.boolAt(seasonInfo, "coverage", "basic_play_by_play")
                || JsonNodes.boolAt(seasonInfo, "coverage", "deeper_play_by_play")) {
            reasons.add("season coverage advertises play-by-play");
        }
        if (!reasons.isEmpty()) {
            return new CoverageDetectionResult(CoverageMode.STANDARD, reasons);
        }
        if (!summary.isMissingNode() && !summary.isNull()) {
            reasons.add("summary score/status is available");
        }
        return new CoverageDetectionResult(CoverageMode.BASIC, reasons);
    }

    private boolean hasRichTimeline(JsonNode payload) {
        return events(payload).stream().anyMatch(event ->
                event.hasNonNull("x")
                        || event.hasNonNull("y")
                        || event.hasNonNull("destination_x")
                        || event.hasNonNull("destination_y")
                        || event.hasNonNull("xg_value")
        );
    }

    private boolean hasExtendedStats(JsonNode payload) {
        JsonNode textSearchRoot = payload == null ? null : payload.path("statistics");
        return containsField(textSearchRoot, "goals_expected")
                || containsField(textSearchRoot, "shots_on_target")
                || containsField(textSearchRoot, "passes_total")
                || containsField(textSearchRoot, "ball_possession");
    }

    private boolean containsField(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.has(fieldName)) {
            return true;
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                if (containsField(child, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasTimeline(JsonNode payload) {
        return !events(payload).isEmpty();
    }

    private boolean hasLineups(JsonNode payload) {
        return payload != null && !payload.path("lineups").isMissingNode() && !payload.path("lineups").isNull();
    }

    private boolean hasMomentum(JsonNode payload) {
        return payload != null && !JsonNodes.array(payload, "momentums").isEmpty();
    }

    private List<JsonNode> events(JsonNode payload) {
        if (payload == null) {
            return List.of();
        }
        JsonNode timeline = payload.path("timeline");
        if (timeline.isArray()) {
            return JsonNodes.array(timeline);
        }
        return JsonNodes.array(timeline, "event");
    }
}
