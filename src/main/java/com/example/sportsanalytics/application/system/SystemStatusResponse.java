package com.example.sportsanalytics.application.system;

import java.time.Instant;
import java.util.List;

public record SystemStatusResponse(
        String application,
        String version,
        String status,
        String database,
        List<String> activeProfiles,
        Instant timestamp
) {
}
