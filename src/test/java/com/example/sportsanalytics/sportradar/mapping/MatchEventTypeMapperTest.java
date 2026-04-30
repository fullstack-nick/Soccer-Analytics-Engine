package com.example.sportsanalytics.sportradar.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.MatchEventType;
import org.junit.jupiter.api.Test;

class MatchEventTypeMapperTest {
    private final MatchEventTypeMapper mapper = new MatchEventTypeMapper();

    @Test
    void mapsConfirmedScoreChangeAsGoalOnlyWhenScoreChanged() {
        assertThat(mapper.map("score_change", true)).isEqualTo(MatchEventType.GOAL);
        assertThat(mapper.map("score_change", false)).isEqualTo(MatchEventType.UNKNOWN);
    }

    @Test
    void mapsPossibleGoalAsShotPressureEvent() {
        assertThat(mapper.map("possible_goal", false)).isEqualTo(MatchEventType.SHOT);
    }
}
