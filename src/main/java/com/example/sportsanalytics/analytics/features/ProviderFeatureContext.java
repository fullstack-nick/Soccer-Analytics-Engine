package com.example.sportsanalytics.analytics.features;

import com.example.sportsanalytics.domain.model.Probability;

public record ProviderFeatureContext(
        Double teamStrengthDelta,
        Probability providerProbability
) {
}
