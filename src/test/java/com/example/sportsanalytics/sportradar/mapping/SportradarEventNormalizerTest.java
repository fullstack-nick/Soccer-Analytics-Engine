package com.example.sportsanalytics.sportradar.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SportradarEventNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SportradarEventNormalizer normalizer = new SportradarEventNormalizer(new MatchEventTypeMapper());

    @Test
    void mapsExtendedTimelineEventsIntoDomainCategories() throws Exception {
        List<NormalizedTimelineEvent> events = normalizer.normalize(
                "sr:sport_event:70075140",
                objectMapper.readTree(getClass().getResourceAsStream("/fixtures/sportradar/extended_timeline.json")),
                TimelineSourceType.EXTENDED,
                UUID.randomUUID()
        );

        assertThat(events).hasSize(4);
        assertThat(events.get(1).eventType()).isEqualTo(MatchEventType.PASS);
        assertThat(events.get(1).teamSide()).isEqualTo(TeamSide.HOME);
        assertThat(events.get(1).x()).isEqualTo(34);
        assertThat(events.get(1).destinationX()).isEqualTo(61);
        assertThat(events.get(1).playerIds()).containsExactly("sr:player:1");
        assertThat(events.get(2).eventType()).isEqualTo(MatchEventType.GOAL);
        assertThat(events.get(2).xgValue()).isEqualTo(0.33);
        assertThat(events.get(3).eventType()).isEqualTo(MatchEventType.CARD);
        assertThat(events.get(3).outcome()).contains("red");
    }

    @Test
    void syntheticEventIdsAreDeterministic() {
        String first = normalizer.syntheticEventId("match-1", 1, "goal", 10, TeamSide.HOME, 1, 0);
        String second = normalizer.syntheticEventId("match-1", 1, "goal", 10, TeamSide.HOME, 1, 0);

        assertThat(first).isEqualTo(second).startsWith("synthetic:");
    }
}
