package com.example.sportsanalytics.application.match.dto;

import java.util.UUID;

public record RebuildMatchStateResult(
        UUID matchId,
        int stateSnapshotsCreated,
        int featureSnapshotsCreated,
        long latestStateVersion
) {
}
