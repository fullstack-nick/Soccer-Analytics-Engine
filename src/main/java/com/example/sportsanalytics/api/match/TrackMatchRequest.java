package com.example.sportsanalytics.api.match;

import jakarta.validation.constraints.NotBlank;

public record TrackMatchRequest(
        @NotBlank String sportEventId,
        boolean forceRefresh
) {
}
