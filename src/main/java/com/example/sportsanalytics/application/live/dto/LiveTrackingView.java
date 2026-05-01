package com.example.sportsanalytics.application.live.dto;

import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.LiveTrackingStatus;
import java.time.Instant;
import java.util.UUID;

public record LiveTrackingView(
        UUID trackingId,
        UUID matchId,
        String providerMatchId,
        LiveTrackingStatus trackingStatus,
        boolean active,
        CoverageMode coverageMode,
        TeamView homeTeam,
        TeamView awayTeam,
        Integer minute,
        Integer homeScore,
        Integer awayScore,
        ProbabilitySnapshotView latestProbability,
        long alertCount,
        Instant startedAt,
        Instant stoppedAt,
        Instant lastPollAt,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        int errorCount,
        String lastError,
        Instant updatedAt
) {
}
