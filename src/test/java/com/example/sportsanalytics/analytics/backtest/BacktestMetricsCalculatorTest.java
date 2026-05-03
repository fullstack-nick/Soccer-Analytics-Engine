package com.example.sportsanalytics.analytics.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestMetricsCalculatorTest {
    private final BacktestMetricsCalculator calculator = new BacktestMetricsCalculator();

    @Test
    void calculatesVersionedMetricsFromFixedMinuteSamplesAndDiagnostics() {
        BacktestMetrics metrics = calculator.calculate(List.of(new BacktestMatchSample(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sr:sport_event:1",
                Outcome.HOME_WIN,
                List.of(
                        sample(1L, "PERIOD", 0, 0, 0, 0.45, 0.30, 0.25, 0.50, 0.25, 0.25),
                        sample(2L, "SHOT", 15, 0, 0, 0.50, 0.30, 0.20, null, null, null),
                        sample(3L, "GOAL", 60, 1, 0, 0.80, 0.15, 0.05, null, null, null),
                        sample(4L, "PERIOD", 90, 2, 0, 0.98, 0.01, 0.01, null, null, null)
                )
        )));

        assertThat(metrics.evaluationVersion()).isEqualTo("stage5.5-v1");
        assertThat(metrics.headline().matchCount()).isEqualTo(1);
        assertThat(metrics.headline().sampleCount()).isEqualTo(7);
        assertThat(metrics.allInPlay().sampleCount()).isEqualTo(3);
        assertThat(metrics.finalSnapshotDiagnostic().matchCount()).isEqualTo(1);
        assertThat(metrics.finalSnapshotDiagnostic().topPickAccuracy()).isEqualTo(1.0);
        assertThat(metrics.fixedMinuteMetrics())
                .extracting(FixedMinuteMetric::label)
                .containsExactly("0", "15", "30", "HT", "60", "75", "85");
        assertThat(metrics.fixedMinuteMetrics())
                .filteredOn(metric -> metric.label().equals("30"))
                .singleElement()
                .satisfies(metric -> assertThat(metric.metrics().sampleCount()).isEqualTo(1));
        assertThat(metrics.baselines().random().sampleCount()).isEqualTo(7);
        assertThat(metrics.baselines().scoreOnly().sampleCount()).isEqualTo(7);
        assertThat(metrics.baselines().providerPreMatch().sampleCount()).isEqualTo(7);
        assertThat(metrics.minuteBucketCalibration())
                .extracting(MinuteBucketCalibration::bucket)
                .containsExactly("0-15", "16-30", "31-45", "46-60", "61-75", "76-85", "86-89");
        assertThat(metrics.eventMovement().get("GOAL"))
                .isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(metrics.matchMetrics()).singleElement().satisfies(match -> {
            assertThat(match.probabilitySnapshots()).isEqualTo(4);
            assertThat(match.inPlaySnapshots()).isEqualTo(3);
            assertThat(match.fixedMinuteSamples()).isEqualTo(7);
            assertThat(match.finalSnapshotTopPickCorrect()).isTrue();
        });
    }

    @Test
    void excludesFinalSnapshotsFromHeadlineMetrics() {
        BacktestMetrics metrics = calculator.calculate(List.of(new BacktestMatchSample(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "sr:sport_event:2",
                Outcome.AWAY_WIN,
                List.of(sample(1L, "PERIOD", 90, 0, 2, 0.01, 0.01, 0.98, null, null, null))
        )));

        assertThat(metrics.headline().sampleCount()).isZero();
        assertThat(metrics.finalSnapshotDiagnostic().matchCount()).isEqualTo(1);
        assertThat(metrics.finalSnapshotDiagnostic().topPickAccuracy()).isEqualTo(1.0);
    }

    @Test
    void minuteBucketCalibrationUsesInPlayMinuteRanges() {
        BacktestMetrics metrics = calculator.calculate(List.of(new BacktestMatchSample(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "sr:sport_event:3",
                Outcome.DRAW,
                List.of(
                        sample(1L, "SHOT", 14, 0, 0, 0.30, 0.45, 0.25, null, null, null),
                        sample(2L, "SHOT", 76, 1, 1, 0.25, 0.50, 0.25, null, null, null),
                        sample(3L, "SHOT", 90, 1, 1, 0.05, 0.90, 0.05, null, null, null)
                )
        )));

        Map<String, MinuteBucketCalibration> buckets = metrics.minuteBucketCalibration().stream()
                .collect(java.util.stream.Collectors.toMap(
                        MinuteBucketCalibration::bucket,
                        bucket -> bucket
                ));
        assertThat(buckets.get("0-15").sampleCount()).isEqualTo(1);
        assertThat(buckets.get("76-85").sampleCount()).isEqualTo(1);
        assertThat(buckets.get("86-89").sampleCount()).isZero();
    }

    @Test
    void eventMovementKeepsSpecificCardTypesSeparate() {
        BacktestMetrics metrics = calculator.calculate(List.of(new BacktestMatchSample(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "sr:sport_event:4",
                Outcome.HOME_WIN,
                List.of(
                        sample(1L, "PERIOD", 0, 0, 0, 0.40, 0.35, 0.25, null, null, null),
                        sample(2L, "YELLOW_CARD", 20, 0, 0, 0.41, 0.34, 0.25, null, null, null),
                        sample(3L, "RED_CARD", 40, 0, 0, 0.55, 0.30, 0.15, null, null, null),
                        sample(4L, "SECOND_YELLOW_RED_CARD", 70, 0, 0, 0.66, 0.24, 0.10, null, null, null)
                )
        )));

        assertThat(metrics.eventMovement()).containsKeys(
                "YELLOW_CARD",
                "RED_CARD",
                "SECOND_YELLOW_RED_CARD"
        );
        assertThat(metrics.eventMovement().get("YELLOW_CARD"))
                .isCloseTo(0.02, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(metrics.eventMovement().get("RED_CARD"))
                .isCloseTo(0.28, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(metrics.eventMovement().get("SECOND_YELLOW_RED_CARD"))
                .isCloseTo(0.22, org.assertj.core.data.Offset.offset(0.000001));
    }

    private BacktestProbabilitySample sample(
            Long eventSequence,
            String eventType,
            int minute,
            int homeScore,
            int awayScore,
            double homeWin,
            double draw,
            double awayWin,
            Double providerHomeWin,
            Double providerDraw,
            Double providerAwayWin
    ) {
        return new BacktestProbabilitySample(
                eventSequence,
                eventType,
                minute,
                homeScore,
                awayScore,
                homeWin,
                draw,
                awayWin,
                providerHomeWin,
                providerDraw,
                providerAwayWin
        );
    }
}
