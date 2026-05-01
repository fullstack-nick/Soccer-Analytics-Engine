package com.example.sportsanalytics.analytics.comparison;

import java.util.UUID;

public record ModelComparisonTimelinePoint(
        UUID eventId,
        Long eventSequence,
        int minute,
        double modelHomeWin,
        double modelDraw,
        double modelAwayWin,
        double providerHomeWin,
        double providerDraw,
        double providerAwayWin,
        double divergence
) {
}
