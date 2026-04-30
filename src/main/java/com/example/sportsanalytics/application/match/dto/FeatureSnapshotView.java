package com.example.sportsanalytics.application.match.dto;

import com.example.sportsanalytics.domain.model.CoverageMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FeatureSnapshotView(
        UUID id,
        UUID matchId,
        UUID eventId,
        Long eventSequence,
        int minute,
        CoverageMode coverageMode,
        String featureSetVersion,
        Map<String, Object> features,
        Map<String, Object> availability,
        Instant createdAt
) {
}
