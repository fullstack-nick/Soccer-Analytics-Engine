package com.example.sportsanalytics.analytics.features;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.analytics.state.EventSourcedMatchState;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchFeatureExtractorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MatchFeatureExtractor extractor = new MatchFeatureExtractor(objectMapper);

    @Test
    void extractsRollingFeaturesWithoutUsingFutureMomentum() throws Exception {
        MatchEventEntity oldMomentumShot = event(1, "shot_on_target", MatchEventType.SHOT, TeamSide.HOME, 30, 80, 50, 0.20);
        MatchEventEntity currentPossibleGoal = event(2, "possible_goal", MatchEventType.SHOT, TeamSide.HOME, 55, 88, 40, null);
        EventSourcedMatchState state = new EventSourcedMatchState(
                2,
                currentPossibleGoal,
                55,
                1,
                0,
                0,
                1,
                objectMapper.createObjectNode(),
                List.of(oldMomentumShot, currentPossibleGoal)
        );

        FeatureExtractionResult result = extractor.extract(
                match(),
                state,
                new FeatureSourceContext(
                        CoverageMode.RICH,
                        fixture("lineups"),
                        objectMapper.readTree("""
                                {
                                  "momentums": [
                                    {"match_time": 40, "value": 4},
                                    {"match_time": 55, "value": 12},
                                    {"match_time": 66, "value": -20}
                                  ]
                                }
                                """),
                        objectMapper.createObjectNode(),
                        objectMapper.createObjectNode(),
                        objectMapper.createObjectNode(),
                        new ProviderFeatureContext(0.6, null)
                )
        );

        assertThat(result.featuresJson().path("scoreDifference").asInt()).isEqualTo(1);
        assertThat(result.featuresJson().path("redCardAdjustment").asDouble()).isEqualTo(0.15);
        assertThat(result.featuresJson().path("shotPressureDelta").isNumber()).isTrue();
        assertThat(result.featuresJson().path("momentumTrend").asDouble()).isEqualTo(8.0);
        assertThat(result.availabilityJson().path("availableFeatures").toString()).contains("shotPressureDelta");
        assertThat(result.availabilityJson().path("missingFeatures").toString()).contains("providerProbability");
    }

    private MatchEntity match() {
        MatchEntity match = new MatchEntity();
        match.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        match.setProviderMatchId("sr:sport_event:test");
        match.setCoverageMode(CoverageMode.RICH);
        return match;
    }

    private MatchEventEntity event(
            long sequence,
            String providerType,
            MatchEventType type,
            TeamSide side,
            int minute,
            Integer x,
            Integer y,
            Double xg
    ) {
        MatchEventEntity event = new MatchEventEntity();
        event.setId(UUID.randomUUID());
        event.setProviderEventId("event-" + sequence);
        event.setProviderEventType(providerType);
        event.setEventSequence(sequence);
        event.setEventType(type);
        event.setTeamSide(side);
        event.setOccurredAtMinute(minute);
        event.setX(x);
        event.setY(y);
        event.setXgValue(xg);
        event.setSourceTimelineType(TimelineSourceType.EXTENDED);
        return event;
    }

    private com.fasterxml.jackson.databind.JsonNode fixture(String name) throws Exception {
        return objectMapper.readTree(getClass().getResourceAsStream("/fixtures/sportradar/" + name + ".json"));
    }
}
