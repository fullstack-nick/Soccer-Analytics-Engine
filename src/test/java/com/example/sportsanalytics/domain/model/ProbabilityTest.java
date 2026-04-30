package com.example.sportsanalytics.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProbabilityTest {
    @Test
    void rejectsProbabilitiesThatDoNotSumToOne() {
        assertThatThrownBy(() -> new Probability(0.50, 0.30, 0.30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    @Test
    void rejectsProbabilitiesOutsideValidRange() {
        assertThatThrownBy(() -> new Probability(1.20, -0.10, -0.10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }
}
