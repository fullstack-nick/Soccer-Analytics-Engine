package com.example.sportsanalytics.application.match.dto;

import java.util.UUID;

public record RebuildProbabilityResult(
        UUID matchId,
        int probabilitySnapshotsCreated
) {
}
