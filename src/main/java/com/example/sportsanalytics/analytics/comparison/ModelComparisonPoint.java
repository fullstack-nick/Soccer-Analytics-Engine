package com.example.sportsanalytics.analytics.comparison;

import java.util.UUID;

public record ModelComparisonPoint(
        UUID eventId,
        Long eventSequence,
        int minute,
        double modelHomeWin,
        double modelDraw,
        double modelAwayWin,
        Double providerHomeWin,
        Double providerDraw,
        Double providerAwayWin
) {
}
