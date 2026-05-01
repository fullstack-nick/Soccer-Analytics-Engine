package com.example.sportsanalytics.sportradar.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveSportradarMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LiveSportradarMapper mapper = new LiveSportradarMapper(
            new SportradarEventNormalizer(new MatchEventTypeMapper())
    );

    @Test
    void extractsLiveSchedulesFromSummaries() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "summaries": [
                    {
                      "sport_event": {"id": "sr:sport_event:1"},
                      "sport_event_status": {"status": "live", "match_status": "2nd_half"}
                    },
                    {
                      "sport_event": {"id": "sr:sport_event:2"},
                      "sport_event_status": {"status": "closed", "match_status": "ended"}
                    }
                  ]
                }
                """);

        List<LiveScheduleEntry> entries = mapper.schedules(payload);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).providerMatchId()).isEqualTo("sr:sport_event:1");
        assertThat(entries.get(0).ended()).isFalse();
        assertThat(entries.get(1).ended()).isTrue();
    }

    @Test
    void extractsTimelineDeltaBatchesAndUsesStableLiveSyntheticIds() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "summaries": [
                    {
                      "sport_event": {"id": "sr:sport_event:1"},
                      "timeline": [
                        {"type": "possible_goal", "match_time": 74, "competitor": "home", "home_score": 1, "away_score": 1, "x": 84, "y": 41}
                      ]
                    }
                  ]
                }
                """);
        UUID rawPayloadId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        List<LiveTimelineEventBatch> first = mapper.timelineBatches(payload, TimelineSourceType.LIVE_DELTA, rawPayloadId);
        List<LiveTimelineEventBatch> second = mapper.timelineBatches(payload, TimelineSourceType.LIVE_DELTA, rawPayloadId);

        assertThat(first).hasSize(1);
        NormalizedTimelineEvent event = first.get(0).events().getFirst();
        assertThat(event.providerEventId()).startsWith("synthetic-live:");
        assertThat(event.providerEventId()).isEqualTo(second.get(0).events().getFirst().providerEventId());
        assertThat(event.eventType()).isEqualTo(MatchEventType.SHOT);
        assertThat(event.sourceTimelineType()).isEqualTo(TimelineSourceType.LIVE_DELTA);
    }

    @Test
    void extractsTimelineDeltaBatchesFromSportradarDeltaWrapper() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "sport_event_timeline_deltas": [
                    {
                      "sport_event_timeline": {
                        "sport_event": {"id": "sr:sport_event:9"},
                        "timeline": [
                          {"id": 101, "type": "corner_kick", "match_time": 52, "competitor": "away"}
                        ]
                      }
                    }
                  ]
                }
                """);
        UUID rawPayloadId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        List<LiveTimelineEventBatch> batches = mapper.timelineBatches(
                payload,
                TimelineSourceType.LIVE_DELTA,
                rawPayloadId
        );

        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst().providerMatchId()).isEqualTo("sr:sport_event:9");
        assertThat(batches.getFirst().events()).hasSize(1);
        assertThat(batches.getFirst().events().getFirst().eventType()).isEqualTo(MatchEventType.SET_PIECE);
    }
}
