package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.MatchEventType;
import org.springframework.stereotype.Component;

@Component
public class MatchEventTypeMapper {
    public MatchEventType map(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return MatchEventType.UNKNOWN;
        }
        String type = providerType.toLowerCase();
        if ((type.contains("goal") && !type.contains("goal_kick") && !type.contains("goal_prevented"))
                || type.equals("score_change")) {
            return MatchEventType.GOAL;
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
}
