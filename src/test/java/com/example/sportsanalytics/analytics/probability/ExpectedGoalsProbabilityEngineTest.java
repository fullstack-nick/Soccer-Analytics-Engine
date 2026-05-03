package com.example.sportsanalytics.analytics.probability;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.FeatureSnapshot;
import com.example.sportsanalytics.domain.model.MatchState;
import com.example.sportsanalytics.domain.model.Probability;
import com.example.sportsanalytics.domain.model.ProbabilitySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExpectedGoalsProbabilityEngineTest {
    private final ExpectedGoalsProbabilityEngine engine = new ExpectedGoalsProbabilityEngine();

    @Test
    void probabilitiesAlwaysSumToOneAndStayInsideBounds() {
        ProbabilitySnapshot snapshot = engine.calculate(state(55, 1, 1), richFeature(55, 0));

        Probability probability = snapshot.probability();
        assertThat(probability.homeWin()).isBetween(0.0, 1.0);
        assertThat(probability.draw()).isBetween(0.0, 1.0);
        assertThat(probability.awayWin()).isBetween(0.0, 1.0);
        assertThat(probability.homeWin() + probability.draw() + probability.awayWin()).isCloseTo(1.0, withinTolerance());
    }

    @Test
    void homeLeadLateProducesStrongHomeWinProbability() {
        ProbabilitySnapshot snapshot = engine.calculate(state(80, 1, 0), richFeature(80, 1));

        assertThat(snapshot.probability().homeWin()).isGreaterThan(0.75);
        assertThat(snapshot.probability().homeWin()).isGreaterThan(snapshot.probability().draw());
        assertThat(snapshot.probability().homeWin()).isGreaterThan(snapshot.probability().awayWin());
    }

    @Test
    void redCardAdvantageShiftsProbabilityTowardHomeTeam() {
        ProbabilitySnapshot baseline = engine.calculate(state(60, 0, 0), richFeature(60, 0));
        ProbabilitySnapshot awayRedCard = engine.calculate(state(60, 0, 0), richFeatureBuilder(60, 0)
                .redCardAdjustment(0.25)
                .build());

        assertThat(awayRedCard.probability().homeWin()).isGreaterThan(baseline.probability().homeWin());
        assertThat(awayRedCard.probability().awayWin()).isLessThan(baseline.probability().awayWin());
    }

    @Test
    void strongerRedCardAdjustmentMovesProbabilityMoreThanOldWeight() {
        ProbabilitySnapshot oldWeight = engine.calculate(state(60, 0, 0), richFeatureBuilder(60, 0)
                .redCardAdjustment(0.15)
                .build());
        ProbabilitySnapshot newWeight = engine.calculate(state(60, 0, 0), richFeatureBuilder(60, 0)
                .redCardAdjustment(0.25)
                .build());

        assertThat(newWeight.probability().homeWin()).isGreaterThan(oldWeight.probability().homeWin());
    }

    @Test
    void basicCoverageWithMissingRichFeaturesStillProducesProbability() {
        ProbabilitySnapshot snapshot = engine.calculate(
                state(30, 0, 0),
                new FeatureBuilder(30, 0)
                        .coverageMode(CoverageMode.BASIC)
                        .availableFeatures(List.of("scoreDifference", "timeRemainingRatio", "homeAdvantage"))
                        .missingFeatures(List.of("xgDelta", "momentumTrend", "providerProbability"))
                        .build()
        );

        assertThat(snapshot.coverageQuality()).isEqualTo("LOW");
        assertThat(snapshot.modelConfidence()).isBetween(0.25, 0.55);
        assertThat(snapshot.probability().homeWin() + snapshot.probability().draw() + snapshot.probability().awayWin())
                .isCloseTo(1.0, withinTolerance());
    }

    @Test
    void providerProbabilityDoesNotChangeModelOutputButAddsComparisonContext() {
        FeatureSnapshot withoutProvider = richFeature(70, 0);
        FeatureSnapshot withProvider = richFeatureBuilder(70, 0)
                .providerProbability(new Probability(0.10, 0.20, 0.70))
                .availableFeatures(List.of("scoreDifference", "timeRemainingRatio", "homeAdvantage", "providerProbability"))
                .missingFeatures(List.of())
                .build();

        ProbabilitySnapshot baseline = engine.calculate(state(70, 1, 1), withoutProvider);
        ProbabilitySnapshot comparison = engine.calculate(state(70, 1, 1), withProvider);

        assertThat(comparison.probability()).isEqualTo(baseline.probability());
        assertThat(comparison.featureContributions()).containsKey("providerProbabilityDivergence");
        assertThat(comparison.explanations()).anyMatch(value -> value.contains("comparison only"));
    }

    @Test
    void finishedLevelMatchKeepsDrawProbabilityDominant() {
        ProbabilitySnapshot snapshot = engine.calculate(state(90, 1, 1), richFeature(90, 0));

        assertThat(snapshot.probability().draw()).isGreaterThan(0.90);
        assertThat(snapshot.probability().draw()).isGreaterThan(snapshot.probability().homeWin());
        assertThat(snapshot.probability().draw()).isGreaterThan(snapshot.probability().awayWin());
    }

    @Test
    void overconfidenceShrinkagePreservesProbabilityInvariant() {
        ProbabilitySnapshot snapshot = engine.calculate(state(90, 3, 0), richFeature(90, 3));

        Probability probability = snapshot.probability();
        assertThat(snapshot.modelVersion()).isEqualTo("xg-poisson-v1.2");
        assertThat(snapshot.featureContributions()).containsKey("confidenceShrinkage");
        assertThat(snapshot.featureContributions()).containsKey("lateGameConfidenceShrinkage");
        assertThat(probability.homeWin()).isBetween(0.0, 1.0);
        assertThat(probability.draw()).isBetween(0.0, 1.0);
        assertThat(probability.awayWin()).isBetween(0.0, 1.0);
        assertThat(probability.homeWin() + probability.draw() + probability.awayWin()).isCloseTo(1.0, withinTolerance());
    }

    @Test
    void lateGameSmoothingOnlyAppliesToConfidentLateSnapshots() {
        ProbabilitySnapshot minute74 = engine.calculate(state(74, 1, 0), richFeature(74, 1));
        ProbabilitySnapshot minute80 = engine.calculate(state(80, 1, 0), richFeature(80, 1));
        ProbabilitySnapshot minute88 = engine.calculate(state(88, 1, 0), richFeature(88, 1));

        assertThat(minute74.featureContributions().get("lateGameConfidenceShrinkage")).isEqualTo(0.0);
        assertThat(minute80.featureContributions().get("lateGameConfidenceShrinkage")).isBetween(0.0, 0.08);
        assertThat(minute88.featureContributions().get("lateGameConfidenceShrinkage")).isBetween(0.0, 0.12);
        assertThat(minute88.probability().homeWin()).isLessThan(1.0);
        assertThat(minute88.probability().homeWin() + minute88.probability().draw() + minute88.probability().awayWin())
                .isCloseTo(1.0, withinTolerance());
    }

    private MatchState state(int minute, int homeScore, int awayScore) {
        return new MatchState(
                "match-1",
                "home",
                "away",
                CoverageMode.RICH,
                minute,
                homeScore,
                awayScore,
                0,
                0,
                Map.of(),
                null,
                Map.of(),
                Instant.parse("2026-04-30T00:00:00Z")
        );
    }

    private FeatureSnapshot richFeature(int minute, int scoreDifference) {
        return richFeatureBuilder(minute, scoreDifference).build();
    }

    private FeatureBuilder richFeatureBuilder(int minute, int scoreDifference) {
        return new FeatureBuilder(minute, scoreDifference)
                .coverageMode(CoverageMode.RICH)
                .teamStrengthDelta(0.10)
                .redCardAdjustment(0.0)
                .xgDelta(0.20)
                .shotPressureDelta(1.0)
                .shotLocationQualityDelta(0.10)
                .fieldTilt(0.20)
                .possessionPressureDelta(0.10)
                .momentumTrend(5.0)
                .availableFeatures(List.of(
                        "scoreDifference",
                        "timeRemainingRatio",
                        "homeAdvantage",
                        "teamStrengthDelta",
                        "xgDelta",
                        "shotPressureDelta",
                        "momentumTrend"
                ))
                .missingFeatures(List.of("providerProbability"));
    }

    private org.assertj.core.data.Offset<Double> withinTolerance() {
        return org.assertj.core.data.Offset.offset(0.000001);
    }

    private static final class FeatureBuilder {
        private final int minute;
        private final int scoreDifference;
        private CoverageMode coverageMode = CoverageMode.RICH;
        private Double teamStrengthDelta;
        private Double lineupAdjustment;
        private Double redCardAdjustment;
        private Double xgDelta;
        private Double shotPressureDelta;
        private Double shotLocationQualityDelta;
        private Double fieldTilt;
        private Double possessionPressureDelta;
        private Double momentumTrend;
        private Probability providerProbability;
        private List<String> availableFeatures = List.of();
        private List<String> missingFeatures = List.of();

        private FeatureBuilder(int minute, int scoreDifference) {
            this.minute = minute;
            this.scoreDifference = scoreDifference;
        }

        private FeatureBuilder coverageMode(CoverageMode coverageMode) {
            this.coverageMode = coverageMode;
            return this;
        }

        private FeatureBuilder teamStrengthDelta(Double teamStrengthDelta) {
            this.teamStrengthDelta = teamStrengthDelta;
            return this;
        }

        private FeatureBuilder redCardAdjustment(Double redCardAdjustment) {
            this.redCardAdjustment = redCardAdjustment;
            return this;
        }

        private FeatureBuilder xgDelta(Double xgDelta) {
            this.xgDelta = xgDelta;
            return this;
        }

        private FeatureBuilder shotPressureDelta(Double shotPressureDelta) {
            this.shotPressureDelta = shotPressureDelta;
            return this;
        }

        private FeatureBuilder shotLocationQualityDelta(Double shotLocationQualityDelta) {
            this.shotLocationQualityDelta = shotLocationQualityDelta;
            return this;
        }

        private FeatureBuilder fieldTilt(Double fieldTilt) {
            this.fieldTilt = fieldTilt;
            return this;
        }

        private FeatureBuilder possessionPressureDelta(Double possessionPressureDelta) {
            this.possessionPressureDelta = possessionPressureDelta;
            return this;
        }

        private FeatureBuilder momentumTrend(Double momentumTrend) {
            this.momentumTrend = momentumTrend;
            return this;
        }

        private FeatureBuilder providerProbability(Probability providerProbability) {
            this.providerProbability = providerProbability;
            return this;
        }

        private FeatureBuilder availableFeatures(List<String> availableFeatures) {
            this.availableFeatures = availableFeatures;
            return this;
        }

        private FeatureBuilder missingFeatures(List<String> missingFeatures) {
            this.missingFeatures = missingFeatures;
            return this;
        }

        private FeatureSnapshot build() {
            int remaining = Math.max(0, 90 - minute);
            return new FeatureSnapshot(
                    "match-1",
                    minute,
                    scoreDifference,
                    remaining,
                    remaining / 90.0,
                    1.0,
                    teamStrengthDelta,
                    lineupAdjustment,
                    redCardAdjustment,
                    xgDelta,
                    shotPressureDelta,
                    shotLocationQualityDelta,
                    fieldTilt,
                    possessionPressureDelta,
                    momentumTrend,
                    providerProbability,
                    coverageMode,
                    availableFeatures,
                    missingFeatures,
                    Instant.parse("2026-04-30T00:00:00Z")
            );
        }
    }
}
