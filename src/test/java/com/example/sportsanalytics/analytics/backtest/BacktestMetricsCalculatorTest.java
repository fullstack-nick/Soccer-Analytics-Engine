package com.example.sportsanalytics.analytics.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestMetricsCalculatorTest {
    private final BacktestMetricsCalculator calculator = new BacktestMetricsCalculator();

    @Test
    void calculatesBrierLogLossAccuracyCalibrationAndMovement() {
        BacktestMetrics metrics = calculator.calculate(List.of(new BacktestMatchSample(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sr:sport_event:1",
                Outcome.HOME_WIN,
                List.of(
                        new BacktestProbabilitySample(1L, "SHOT", 0.50, 0.30, 0.20),
                        new BacktestProbabilitySample(2L, "GOAL", 0.80, 0.15, 0.05)
                )
        )));

        assertThat(metrics.matchCount()).isEqualTo(1);
        assertThat(metrics.probabilitySnapshotCount()).isEqualTo(2);
        assertThat(metrics.brierScore()).isCloseTo(0.2225, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(metrics.logLoss()).isCloseTo(0.458145, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(metrics.finalSnapshotTopPickAccuracy()).isEqualTo(1.0);
        assertThat(metrics.calibrationBuckets())
                .filteredOn(bucket -> bucket.bucket().equals("0.5-0.6"))
                .singleElement()
                .satisfies(bucket -> assertThat(bucket.count()).isEqualTo(1));
        assertThat(metrics.averageProbabilityMovementByEventType().get("GOAL"))
                .isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.000001));
    }
}
