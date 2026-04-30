package com.example.sportsanalytics.analytics.features;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.fasterxml.jackson.databind.JsonNode;

public record FeatureSourceContext(
        CoverageMode coverageMode,
        JsonNode lineups,
        JsonNode momentum,
        JsonNode standings,
        JsonNode formStandings,
        JsonNode seasonProbabilities,
        ProviderFeatureContext providerContext
) {
}
