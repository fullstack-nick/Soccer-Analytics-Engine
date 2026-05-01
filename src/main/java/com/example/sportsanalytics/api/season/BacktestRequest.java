package com.example.sportsanalytics.api.season;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record BacktestRequest(
        List<@NotBlank String> sportEventIds,
        boolean forceRefresh,
        Boolean continueOnMatchFailure
) {
}
