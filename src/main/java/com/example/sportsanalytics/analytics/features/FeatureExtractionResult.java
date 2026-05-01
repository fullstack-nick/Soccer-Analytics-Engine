package com.example.sportsanalytics.analytics.features;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.fasterxml.jackson.databind.JsonNode;

public record FeatureExtractionResult(
        JsonNode featuresJson,
        JsonNode availabilityJson,
        CoverageMode coverageMode,
        String featureSetVersion
) {
    public static final String VERSION = "stage5.5-v1";
}
