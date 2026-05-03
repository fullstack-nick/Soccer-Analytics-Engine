package com.example.sportsanalytics.domain.model;

public enum MatchEventType {
    GOAL,
    SHOT,
    PASS,
    FOUL,
    YELLOW_CARD,
    RED_CARD,
    SECOND_YELLOW_RED_CARD,
    UNKNOWN_CARD,
    CARD,
    SUBSTITUTION,
    SET_PIECE,
    OFFSIDE,
    PENALTY,
    INJURY,
    PERIOD,
    LINEUP,
    MOMENTUM,
    VAR,
    UNKNOWN;

    public boolean isCardEvent() {
        return this == YELLOW_CARD
                || this == RED_CARD
                || this == SECOND_YELLOW_RED_CARD
                || this == UNKNOWN_CARD
                || this == CARD;
    }

    public boolean isRedCardEvent() {
        return this == RED_CARD || this == SECOND_YELLOW_RED_CARD;
    }
}
