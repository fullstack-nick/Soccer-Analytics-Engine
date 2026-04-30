package com.example.sportsanalytics.domain.probability;

import com.example.sportsanalytics.domain.model.FeatureSnapshot;
import com.example.sportsanalytics.domain.model.MatchState;
import com.example.sportsanalytics.domain.model.ProbabilitySnapshot;

public interface ProbabilityEngine {
    ProbabilitySnapshot calculate(MatchState state, FeatureSnapshot features);
}
