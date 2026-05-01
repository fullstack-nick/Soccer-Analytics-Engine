package com.example.sportsanalytics.application.alert.dto;

import com.example.sportsanalytics.domain.model.AlertSeverity;
import com.example.sportsanalytics.domain.model.AlertType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MatchAlertView(
        UUID id,
        UUID matchId,
        UUID eventId,
        UUID probabilitySnapshotId,
        AlertType alertType,
        AlertSeverity severity,
        int minute,
        String title,
        String message,
        Map<String, Object> details,
        Instant createdAt
) {
    public MatchAlertView {
        details = Map.copyOf(details == null ? Map.of() : details);
    }
}
