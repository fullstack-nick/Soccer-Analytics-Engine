package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.MatchEventType;
import org.springframework.stereotype.Component;

@Component
public class MatchEventTypeMapper {
    public MatchEventType map(String providerType) {
        return map(providerType, false);
    }

    public MatchEventType map(String providerType, boolean scoreChanged) {
        if (providerType == null || providerType.isBlank()) {
            return MatchEventType.UNKNOWN;
        }
        String type = providerType.toLowerCase();
        if (scoreChanged && isConfirmedGoalType(type)) {
            return MatchEventType.GOAL;
        }
        if (type.equals("penalty_awarded") || type.equals("penalty_missed") || type.equals("penalty_saved")) {
            return MatchEventType.PENALTY;
        }
        if (type.equals("possible_goal")) {
            return MatchEventType.SHOT;
        }
        if (type.equals("corner_kick") || type.equals("throw_in") || type.equals("goal_kick")) {
            return MatchEventType.SET_PIECE;
        }
        if (type.equals("offside")) {
            return MatchEventType.OFFSIDE;
        }
        if (type.equals("injury") || type.equals("injury_return") || type.equals("injury_time_shown")) {
            return MatchEventType.INJURY;
        }
        if (type.contains("shot")) {
            return MatchEventType.SHOT;
        }
        if (type.contains("pass") || type.equals("passer")) {
            return MatchEventType.PASS;
        }
        if (type.contains("foul") || type.contains("free_kick")) {
            return MatchEventType.FOUL;
        }
        if (type.contains("card") || type.contains("booking")) {
            return MatchEventType.CARD;
        }
        if (type.contains("substitution")) {
            return MatchEventType.SUBSTITUTION;
        }
        if (type.contains("period") || type.startsWith("match_") || type.contains("break")) {
            return MatchEventType.PERIOD;
        }
        if (type.contains("lineup")) {
            return MatchEventType.LINEUP;
        }
        if (type.contains("momentum")) {
            return MatchEventType.MOMENTUM;
        }
        if (type.contains("var") || type.contains("video_assistant_referee")) {
            return MatchEventType.VAR;
        }
        return MatchEventType.UNKNOWN;
    }

    private boolean isConfirmedGoalType(String type) {
        return type.equals("score_change")
                || type.equals("goal")
                || type.equals("own_goal")
                || type.equals("penalty_goal")
                || type.equals("penalty_shootout_scored");
    }
}
