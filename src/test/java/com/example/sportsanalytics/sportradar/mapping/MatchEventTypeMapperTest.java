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

    @Test
    void mapsRealProviderEventSemantics() {
        assertThat(mapper.map("corner_kick", false)).isEqualTo(MatchEventType.SET_PIECE);
        assertThat(mapper.map("throw_in", false)).isEqualTo(MatchEventType.SET_PIECE);
        assertThat(mapper.map("goal_kick", false)).isEqualTo(MatchEventType.SET_PIECE);
        assertThat(mapper.map("offside", false)).isEqualTo(MatchEventType.OFFSIDE);
        assertThat(mapper.map("penalty_awarded", false)).isEqualTo(MatchEventType.PENALTY);
        assertThat(mapper.map("penalty_missed", false)).isEqualTo(MatchEventType.PENALTY);
        assertThat(mapper.map("penalty_saved", false)).isEqualTo(MatchEventType.PENALTY);
        assertThat(mapper.map("injury", false)).isEqualTo(MatchEventType.INJURY);
        assertThat(mapper.map("injury_return", false)).isEqualTo(MatchEventType.INJURY);
        assertThat(mapper.map("injury_time_shown", false)).isEqualTo(MatchEventType.INJURY);
    }

    @Test
    void mapsCardProviderEventsIntoSpecificCardSemantics() {
        assertThat(mapper.map("yellow_card", false)).isEqualTo(MatchEventType.YELLOW_CARD);
        assertThat(mapper.map("red_card", false)).isEqualTo(MatchEventType.RED_CARD);
        assertThat(mapper.map("yellow_red_card", false)).isEqualTo(MatchEventType.SECOND_YELLOW_RED_CARD);
        assertThat(mapper.map("second_yellow_card", false)).isEqualTo(MatchEventType.SECOND_YELLOW_RED_CARD);
        assertThat(mapper.map("booking", false)).isEqualTo(MatchEventType.YELLOW_CARD);
        assertThat(mapper.map("card", false)).isEqualTo(MatchEventType.UNKNOWN_CARD);
        assertThat(mapper.map("foul", false, "red card")).isEqualTo(MatchEventType.RED_CARD);
    }
}
