package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.CoverageMode;
import java.util.List;

public record CoverageDetectionResult(CoverageMode mode, List<String> reasons) {
    public CoverageDetectionResult {
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }
}
