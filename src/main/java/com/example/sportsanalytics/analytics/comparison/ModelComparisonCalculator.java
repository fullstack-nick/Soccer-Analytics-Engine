package com.example.sportsanalytics.analytics.comparison;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModelComparisonCalculator {
    public ModelComparisonResult compare(UUID matchId, String providerMatchId, List<ModelComparisonPoint> points) {
        List<ModelComparisonTimelinePoint> compared = new ArrayList<>();
        for (ModelComparisonPoint point : points == null ? List.<ModelComparisonPoint>of() : points) {
            if (point.providerHomeWin() == null || point.providerDraw() == null || point.providerAwayWin() == null) {
                continue;
            }
            double divergence = divergence(point);
            compared.add(new ModelComparisonTimelinePoint(
                    point.eventId(),
                    point.eventSequence(),
                    point.minute(),
                    point.modelHomeWin(),
                    point.modelDraw(),
                    point.modelAwayWin(),
                    point.providerHomeWin(),
                    point.providerDraw(),
                    point.providerAwayWin(),
                    divergence
            ));
        }
        if (compared.isEmpty()) {
            return new ModelComparisonResult(
                    matchId,
                    providerMatchId,
                    false,
                    "Provider probability was not available for this match.",
                    0,
                    0.0,
                    0.0,
                    List.of()
            );
        }
        double total = 0.0;
        double max = 0.0;
        for (ModelComparisonTimelinePoint point : compared) {
            total += point.divergence();
            max = Math.max(max, point.divergence());
        }
        return new ModelComparisonResult(
                matchId,
                providerMatchId,
                true,
                "Provider probability comparison available.",
                compared.size(),
                total / compared.size(),
                max,
                compared
        );
    }

    private double divergence(ModelComparisonPoint point) {
        return Math.max(
                Math.abs(point.modelHomeWin() - point.providerHomeWin()),
                Math.max(
                        Math.abs(point.modelDraw() - point.providerDraw()),
                        Math.abs(point.modelAwayWin() - point.providerAwayWin())
                )
        );
    }
}
