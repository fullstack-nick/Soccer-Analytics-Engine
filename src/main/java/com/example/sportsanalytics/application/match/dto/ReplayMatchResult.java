package com.example.sportsanalytics.application.match.dto;

import com.example.sportsanalytics.domain.model.CoverageMode;
import java.util.UUID;

public record ReplayMatchResult(
        UUID matchId,
        String providerMatchId,
        CoverageMode coverageMode,
        int eventCount,
        int stateSnapshotsCreated,
        int featureSnapshotsCreated,
        int probabilitySnapshotsCreated,
        FinalScoreView finalScore,
        ProbabilitySnapshotView latestProbability
) {
}
