package com.example.sportsanalytics.sportradar.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.fasterxml.jackson.databind.JsonNode;
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
        assertThat(events.get(2).providerEventType()).isEqualTo("score_change");
        assertThat(events.get(2).scoreChanged()).isTrue();
        assertThat(events.get(2).homeScoreAfter()).isEqualTo(1);
        assertThat(events.get(2).awayScoreAfter()).isEqualTo(0);
        assertThat(events.get(2).xgValue()).isEqualTo(0.33);
        assertThat(events.get(3).eventType()).isEqualTo(MatchEventType.CARD);
        assertThat(events.get(3).outcome()).contains("red");
    }

    @Test
    void possibleGoalIsPressureEventUnlessScoreChanges() throws Exception {
        JsonNode timeline = objectMapper.readTree("""
                {
                  "timeline": {
                    "event": [
                      {
                        "id": "start",
                        "type": "match_started",
                        "match_time": 0,
                        "home_score": 0,
                        "away_score": 0
                      },
                      {
                        "id": "possible",
                        "type": "possible_goal",
                        "match_time": 20,
                        "competitor": "home",
                        "home_score": 0,
                        "away_score": 0
                      },
                      {
                        "id": "confirmed",
                        "type": "score_change",
                        "match_time": 21,
                        "competitor": "home",
                        "home_score": 1,
                        "away_score": 0
                      }
                    ]
                  }
                }
                """);

        List<NormalizedTimelineEvent> events = normalizer.normalize(
                "sr:sport_event:test",
                timeline,
                TimelineSourceType.EXTENDED,
                UUID.randomUUID()
        );

        assertThat(events.get(1).providerEventType()).isEqualTo("possible_goal");
        assertThat(events.get(1).eventType()).isEqualTo(MatchEventType.SHOT);
        assertThat(events.get(1).scoreChanged()).isFalse();
        assertThat(events.get(2).eventType()).isEqualTo(MatchEventType.GOAL);
        assertThat(events.get(2).scoreChanged()).isTrue();
    }

    @Test
    void detectsAdministrativeOnlyTimelineAsNotUsableForMatchReplay() throws Exception {
        JsonNode timeline = objectMapper.readTree("""
                {
                  "timeline": {
                    "event": [
                      { "id": "league", "type": "league" },
                      { "id": "referee", "type": "main_referee" },
                      { "id": "period", "type": "regular_period" }
                    ]
                  }
                }
                """);

        List<NormalizedTimelineEvent> events = normalizer.normalize(
                "sr:sport_event:test",
                timeline,
                TimelineSourceType.EXTENDED,
                UUID.randomUUID()
        );

        assertThat(normalizer.hasUsableMatchEvents(events)).isFalse();
    }

    @Test
    void detectsTimedTimelineEventsAsUsableForMatchReplay() throws Exception {
        JsonNode timeline = objectMapper.readTree("""
                {
                  "timeline": {
                    "event": [
                      {
                        "id": "shot",
                        "type": "shot_on_target",
                        "match_time": 12,
                        "competitor": "away",
                        "home_score": 0,
                        "away_score": 0
                      }
                    ]
                  }
                }
                """);

        List<NormalizedTimelineEvent> events = normalizer.normalize(
                "sr:sport_event:test",
                timeline,
                TimelineSourceType.STANDARD,
                UUID.randomUUID()
        );

        assertThat(normalizer.hasUsableMatchEvents(events)).isTrue();
    }

    @Test
    void syntheticEventIdsAreDeterministic() {
        String first = normalizer.syntheticEventId("match-1", 1, "goal", 10, TeamSide.HOME, 1, 0);
        String second = normalizer.syntheticEventId("match-1", 1, "goal", 10, TeamSide.HOME, 1, 0);

        assertThat(first).isEqualTo(second).startsWith("synthetic:");
    }
}
