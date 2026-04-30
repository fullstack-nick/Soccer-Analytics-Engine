package com.example.sportsanalytics.config;

import com.example.sportsanalytics.analytics.probability.ExpectedGoalsProbabilityEngine;
import com.example.sportsanalytics.domain.probability.ProbabilityEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProbabilityEngineConfig {
    @Bean
    ProbabilityEngine probabilityEngine() {
        return new ExpectedGoalsProbabilityEngine();
    }
}
