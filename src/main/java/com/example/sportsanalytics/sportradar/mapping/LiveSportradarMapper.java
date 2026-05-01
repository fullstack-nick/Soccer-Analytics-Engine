package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LiveSportradarMapper {
    private final SportradarEventNormalizer eventNormalizer;

    public LiveSportradarMapper(SportradarEventNormalizer eventNormalizer) {
        this.eventNormalizer = eventNormalizer;
    }

    public List<LiveScheduleEntry> schedules(JsonNode payload) {
        Map<String, LiveScheduleEntry> entries = new LinkedHashMap<>();
        collectScheduleEntries(payload, entries);
        for (JsonNode summary : JsonNodes.array(payload, "summaries")) {
            collectScheduleEntries(summary, entries);
        }
        for (JsonNode schedule : JsonNodes.array(payload, "schedules")) {
            collectScheduleEntries(schedule, entries);
        }
        for (JsonNode sportEvent : JsonNodes.array(payload, "sport_events")) {
            collectScheduleEntries(sportEvent, entries);
        }
        return List.copyOf(entries.values());
    }

    public List<LiveTimelineEventBatch> timelineBatches(
            JsonNode payload,
            TimelineSourceType sourceType,
            UUID rawPayloadId
    ) {
        Map<String, LiveTimelineEventBatch> batches = new LinkedHashMap<>();
        collectTimelineBatch(payload, sourceType, rawPayloadId, batches);
        for (JsonNode summary : JsonNodes.array(payload, "summaries")) {
            collectTimelineBatch(summary, sourceType, rawPayloadId, batches);
        }
        for (JsonNode timeline : JsonNodes.array(payload, "sport_event_timelines")) {
            collectTimelineBatch(timeline, sourceType, rawPayloadId, batches);
        }
        for (JsonNode timeline : JsonNodes.array(payload, "timelines")) {
            collectTimelineBatch(timeline, sourceType, rawPayloadId, batches);
        }
        return List.copyOf(batches.values());
    }

    private void collectScheduleEntries(JsonNode node, Map<String, LiveScheduleEntry> entries) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        JsonNode sportEvent = node.path("sport_event").isObject() ? node.path("sport_event") : node;
        String providerMatchId = JsonNodes.text(sportEvent, "id");
        if (providerMatchId == null) {
            return;
        }
        JsonNode statusNode = node.path("sport_event_status");
        entries.put(providerMatchId, new LiveScheduleEntry(
                providerMatchId,
                JsonNodes.text(statusNode, "status"),
                JsonNodes.text(statusNode, "match_status")
        ));
    }

    private void collectTimelineBatch(
            JsonNode node,
            TimelineSourceType sourceType,
            UUID rawPayloadId,
            Map<String, LiveTimelineEventBatch> batches
    ) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String providerMatchId = providerMatchId(node);
        JsonNode timelinePayload = timelinePayload(node);
        if (providerMatchId == null || timelineEvents(timelinePayload).isEmpty()) {
            return;
        }
        List<NormalizedTimelineEvent> events = new ArrayList<>(eventNormalizer.normalize(
                providerMatchId,
                timelinePayload,
                sourceType,
                rawPayloadId
        ));
        batches.merge(
                providerMatchId,
                new LiveTimelineEventBatch(providerMatchId, sourceType, rawPayloadId, events),
                (left, right) -> new LiveTimelineEventBatch(
                        left.providerMatchId(),
                        left.sourceType(),
                        left.rawPayloadId(),
                        mergeEvents(left.events(), right.events())
                )
        );
    }

    private String providerMatchId(JsonNode node) {
        String id = JsonNodes.textAt(node, "sport_event", "id");
        if (id != null) {
            return id;
        }
        id = JsonNodes.textAt(node, "sport_event_timeline", "sport_event", "id");
        if (id != null) {
            return id;
        }
        return JsonNodes.text(node, "id");
    }

    private List<JsonNode> timelineEvents(JsonNode node) {
        List<JsonNode> events = JsonNodes.array(node.path("timeline"), "event");
        if (!events.isEmpty()) {
            return events;
        }
        return JsonNodes.array(node.path("timeline"));
    }

    private JsonNode timelinePayload(JsonNode node) {
        JsonNode nested = node.path("sport_event_timeline");
        return nested.isObject() ? nested : node;
    }

    private List<NormalizedTimelineEvent> mergeEvents(
            List<NormalizedTimelineEvent> left,
            List<NormalizedTimelineEvent> right
    ) {
        Map<String, NormalizedTimelineEvent> merged = new LinkedHashMap<>();
        left.forEach(event -> merged.put(event.providerEventId(), event));
        right.forEach(event -> merged.put(event.providerEventId(), event));
        return List.copyOf(merged.values());
    }
}
