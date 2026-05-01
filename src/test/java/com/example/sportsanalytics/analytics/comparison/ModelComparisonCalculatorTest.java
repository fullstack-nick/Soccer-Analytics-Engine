package com.example.sportsanalytics.analytics.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelComparisonCalculatorTest {
    private final ModelComparisonCalculator calculator = new ModelComparisonCalculator();

    @Test
    void returnsDivergenceWhenProviderProbabilityExists() {
        ModelComparisonResult result = calculator.compare(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sr:sport_event:1",
                List.of(new ModelComparisonPoint(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        1L,
                        50,
                        0.70,
                        0.20,
                        0.10,
                        0.60,
                        0.25,
                        0.15
                ))
        );

        assertThat(result.providerAvailable()).isTrue();
        assertThat(result.comparedSnapshotCount()).isEqualTo(1);
        assertThat(result.maxDivergence()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.timeline()).hasSize(1);
    }

    @Test
    void returnsUnavailableResultWhenProviderProbabilityIsMissing() {
        ModelComparisonResult result = calculator.compare(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sr:sport_event:1",
                List.of(new ModelComparisonPoint(null, null, 0, 0.40, 0.30, 0.30, null, null, null))
        );

        assertThat(result.providerAvailable()).isFalse();
        assertThat(result.comparedSnapshotCount()).isZero();
        assertThat(result.reason()).contains("not available");
    }
}
