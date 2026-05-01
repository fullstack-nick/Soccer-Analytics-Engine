package com.example.sportsanalytics.analytics.comparison;

import java.util.List;
import java.util.UUID;

public record ModelComparisonResult(
        UUID matchId,
        String providerMatchId,
        boolean providerAvailable,
        String reason,
        int comparedSnapshotCount,
        double averageDivergence,
        double maxDivergence,
        List<ModelComparisonTimelinePoint> timeline
) {
    public ModelComparisonResult {
        timeline = List.copyOf(timeline == null ? List.of() : timeline);
    }
}
