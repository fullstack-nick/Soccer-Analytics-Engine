package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SportradarEventNormalizer {
    private final MatchEventTypeMapper eventTypeMapper;

    public SportradarEventNormalizer(MatchEventTypeMapper eventTypeMapper) {
        this.eventTypeMapper = eventTypeMapper;
    }

    public List<NormalizedTimelineEvent> normalize(
            String providerMatchId,
            JsonNode timelinePayload,
            TimelineSourceType sourceType,
            UUID rawPayloadId
    ) {
        List<JsonNode> rawEvents = JsonNodes.array(timelinePayload.path("timeline"), "event");
        if (rawEvents.isEmpty() && timelinePayload.path("timeline").isArray()) {
            rawEvents = JsonNodes.array(timelinePayload.path("timeline"));
        }
        List<NormalizedTimelineEvent> events = new ArrayList<>();
        long sequence = 1;
        for (JsonNode rawEvent : rawEvents) {
            String providerEventId = JsonNodes.text(rawEvent, "id");
            Integer minute = JsonNodes.integer(rawEvent, "match_time");
            Integer homeScore = JsonNodes.integer(rawEvent, "home_score");
            Integer awayScore = JsonNodes.integer(rawEvent, "away_score");
            String providerType = JsonNodes.text(rawEvent, "type");
            TeamSide side = teamSide(rawEvent);
            String normalizedProviderEventId = providerEventId == null
                    ? syntheticEventId(providerMatchId, sequence, providerType, minute, side, homeScore, awayScore)
                    : providerEventId;
            events.add(new NormalizedTimelineEvent(
                    normalizedProviderEventId,
                    sequence,
                    eventTypeMapper.map(providerType),
                    minute == null ? 0 : minute,
                    JsonNodes.integer(rawEvent, "stoppage_time"),
                    side,
                    playerIds(rawEvent),
                    JsonNodes.integer(rawEvent, "x"),
                    JsonNodes.integer(rawEvent, "y"),
                    JsonNodes.integer(rawEvent, "destination_x"),
                    JsonNodes.integer(rawEvent, "destination_y"),
                    JsonNodes.decimal(rawEvent, "xg_value"),
                    firstNonBlank(
                            JsonNodes.text(rawEvent, "outcome"),
                            JsonNodes.text(rawEvent, "card_description"),
                            JsonNodes.text(rawEvent, "reason"),
                            JsonNodes.text(rawEvent, "decision"),
                            JsonNodes.text(rawEvent, "method"),
                            providerType
                    ),
                    homeScore,
                    awayScore,
                    sourceType,
                    rawPayloadId
            ));
            sequence++;
        }
        return events.stream()
                .sorted(Comparator.comparingLong(NormalizedTimelineEvent::sequence))
                .toList();
    }

    public String syntheticEventId(
            String providerMatchId,
            long sequence,
            String providerType,
            Integer minute,
            TeamSide side,
            Integer homeScore,
            Integer awayScore
    ) {
        String source = providerMatchId + "|" + sequence + "|" + providerType + "|" + minute + "|" + side + "|"
                + homeScore + "|" + awayScore;
        return "synthetic:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private TeamSide teamSide(JsonNode rawEvent) {
        String value = firstNonBlank(
                JsonNodes.text(rawEvent, "competitor"),
                JsonNodes.text(rawEvent, "team"),
                JsonNodes.text(rawEvent, "qualifier")
        );
        if (value == null) {
            return TeamSide.UNKNOWN;
        }
        return switch (value.toLowerCase()) {
            case "home" -> TeamSide.HOME;
            case "away" -> TeamSide.AWAY;
            case "neutral" -> TeamSide.NEUTRAL;
            default -> TeamSide.UNKNOWN;
        };
    }

    private List<String> playerIds(JsonNode rawEvent) {
        JsonNode players = rawEvent.path("players");
        List<JsonNode> playerNodes = players.isMissingNode() || players.isNull()
                ? List.of()
                : JsonNodes.array(players, "player");
        return playerNodes.stream()
                .map(player -> JsonNodes.text(player, "id"))
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
