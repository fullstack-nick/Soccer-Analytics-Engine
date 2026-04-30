package com.example.sportsanalytics.analytics.probability;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.FeatureSnapshot;
import com.example.sportsanalytics.domain.model.MatchState;
import com.example.sportsanalytics.domain.model.Probability;
import com.example.sportsanalytics.domain.model.ProbabilitySnapshot;
import com.example.sportsanalytics.domain.probability.ProbabilityEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpectedGoalsProbabilityEngine implements ProbabilityEngine {
    public static final String MODEL_VERSION = "xg-poisson-v1";

    private static final int MAX_REMAINING_GOALS = 8;
    private static final double MIN_EXPECTED_GOALS = 0.02;
    private static final double MAX_EXPECTED_GOALS = 4.0;

    @Override
    public ProbabilitySnapshot calculate(MatchState state, FeatureSnapshot features) {
        double timeRemainingRatio = clamp(features.timeRemainingRatio(), 0.0, 1.25);
        double baseHomeGoals = 1.35 * timeRemainingRatio;
        double baseAwayGoals = 1.10 * timeRemainingRatio;

        Map<String, Double> contributions = new LinkedHashMap<>();
        double homeAttackAdvantage = 0.0;
        homeAttackAdvantage += contribution(contributions, "teamStrengthDelta",
                features.teamStrengthDelta(), 0.08, 0.25);
        homeAttackAdvantage += contribution(contributions, "homeAdvantage",
                features.homeAdvantage(), 0.05, 0.08);
        homeAttackAdvantage += contribution(contributions, "lineupAdjustment",
                features.lineupAdjustment(), 1.00, 0.18);
        homeAttackAdvantage += contribution(contributions, "redCardAdjustment",
                features.redCardAdjustment(), 1.00, 0.35);
        homeAttackAdvantage += scoreStateContribution(contributions, state, timeRemainingRatio);
        homeAttackAdvantage += contribution(contributions, "xgDelta",
                features.xgDelta(), 0.10, 0.22);
        homeAttackAdvantage += contribution(contributions, "shotPressureDelta",
                features.shotPressureDelta(), 0.025, 0.20);
        homeAttackAdvantage += contribution(contributions, "shotLocationQualityDelta",
                features.shotLocationQualityDelta(), 0.12, 0.16);
        homeAttackAdvantage += contribution(contributions, "fieldTilt",
                features.fieldTilt(), 0.12, 0.14);
        homeAttackAdvantage += contribution(contributions, "possessionPressureDelta",
                features.possessionPressureDelta(), 0.08, 0.10);
        homeAttackAdvantage += contribution(contributions, "momentumTrend",
                features.momentumTrend(), 0.005, 0.18);

        double homeExpectedGoalsRemaining = clamp(
                baseHomeGoals * Math.exp(homeAttackAdvantage),
                MIN_EXPECTED_GOALS,
                MAX_EXPECTED_GOALS
        );
        double awayExpectedGoalsRemaining = clamp(
                baseAwayGoals * Math.exp(-homeAttackAdvantage),
                MIN_EXPECTED_GOALS,
                MAX_EXPECTED_GOALS
        );
        contributions.put("expectedHomeGoalsRemaining", homeExpectedGoalsRemaining);
        contributions.put("expectedAwayGoalsRemaining", awayExpectedGoalsRemaining);

        Probability probability = simulateOutcomeProbability(
                state.homeScore(),
                state.awayScore(),
                homeExpectedGoalsRemaining,
                awayExpectedGoalsRemaining
        );

        double confidence = modelConfidence(features);
        String coverageQuality = coverageQuality(confidence);
        if (features.providerProbability() != null) {
            contributions.put("providerProbabilityDivergence",
                    providerDivergence(probability, features.providerProbability()));
        }

        return new ProbabilitySnapshot(
                features.matchId(),
                null,
                features.minute(),
                probability,
                features.coverageMode(),
                MODEL_VERSION,
                confidence,
                coverageQuality,
                explanations(state, features, probability, homeExpectedGoalsRemaining, awayExpectedGoalsRemaining, coverageQuality),
                contributions,
                Instant.now()
        );
    }

    private double contribution(
            Map<String, Double> contributions,
            String featureName,
            Double value,
            double weight,
            double limit
    ) {
        if (value == null || !Double.isFinite(value)) {
            return 0.0;
        }
        double contribution = clamp(value * weight, -limit, limit);
        contributions.put(featureName, contribution);
        return contribution;
    }

    private double scoreStateContribution(
            Map<String, Double> contributions,
            MatchState state,
            double timeRemainingRatio
    ) {
        int scoreDifference = state.homeScore() - state.awayScore();
        if (scoreDifference == 0) {
            contributions.put("scoreStateAdjustment", 0.0);
            return 0.0;
        }
        double elapsedRatio = 1.0 - clamp(timeRemainingRatio, 0.0, 1.0);
        double contribution = clamp(-scoreDifference * 0.06 * elapsedRatio, -0.22, 0.22);
        contributions.put("scoreStateAdjustment", contribution);
        return contribution;
    }

    private Probability simulateOutcomeProbability(
            int currentHomeScore,
            int currentAwayScore,
            double homeExpectedGoalsRemaining,
            double awayExpectedGoalsRemaining
    ) {
        double[] homeDistribution = poissonDistribution(homeExpectedGoalsRemaining);
        double[] awayDistribution = poissonDistribution(awayExpectedGoalsRemaining);

        double homeWin = 0.0;
        double draw = 0.0;
        double awayWin = 0.0;
        for (int homeGoals = 0; homeGoals <= MAX_REMAINING_GOALS; homeGoals++) {
            for (int awayGoals = 0; awayGoals <= MAX_REMAINING_GOALS; awayGoals++) {
                double probability = homeDistribution[homeGoals] * awayDistribution[awayGoals];
                int finalHomeScore = currentHomeScore + homeGoals;
                int finalAwayScore = currentAwayScore + awayGoals;
                if (finalHomeScore > finalAwayScore) {
                    homeWin += probability;
                } else if (finalHomeScore == finalAwayScore) {
                    draw += probability;
                } else {
                    awayWin += probability;
                }
            }
        }
        double total = homeWin + draw + awayWin;
        if (!Double.isFinite(total) || total <= 0.0) {
            return new Probability(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0);
        }
        return new Probability(homeWin / total, draw / total, awayWin / total);
    }

    private double[] poissonDistribution(double lambda) {
        double[] probabilities = new double[MAX_REMAINING_GOALS + 1];
        double value = Math.exp(-lambda);
        double cumulative = value;
        probabilities[0] = value;
        for (int goals = 1; goals < MAX_REMAINING_GOALS; goals++) {
            value *= lambda / goals;
            probabilities[goals] = value;
            cumulative += value;
        }
        probabilities[MAX_REMAINING_GOALS] = Math.max(0.0, 1.0 - cumulative);
        return probabilities;
    }

    private double modelConfidence(FeatureSnapshot features) {
        double base = switch (features.coverageMode()) {
            case RICH -> 0.74;
            case STANDARD -> 0.58;
            case BASIC -> 0.40;
        };
        int available = features.availableFeatures().size();
        int missing = features.missingFeatures().size();
        double availabilityRatio = available + missing == 0 ? 0.5 : (double) available / (available + missing);
        return clamp(base + ((availabilityRatio - 0.5) * 0.25), 0.25, 0.95);
    }

    private String coverageQuality(double confidence) {
        if (confidence >= 0.75) {
            return "HIGH";
        }
        if (confidence >= 0.55) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> explanations(
            MatchState state,
            FeatureSnapshot features,
            Probability probability,
            double homeExpectedGoalsRemaining,
            double awayExpectedGoalsRemaining,
            String coverageQuality
    ) {
        List<String> explanations = new ArrayList<>();
        explanations.add("Score is " + state.homeScore() + "-" + state.awayScore()
                + " in minute " + features.minute() + ".");
        explanations.add("Expected remaining goals are "
                + rounded(homeExpectedGoalsRemaining) + " home and "
                + rounded(awayExpectedGoalsRemaining) + " away.");
        explanations.add("Coverage quality is " + coverageQuality + " for " + features.coverageMode() + " coverage.");

        if (nonZero(features.redCardAdjustment())) {
            explanations.add(features.redCardAdjustment() > 0
                    ? "Away red-card disadvantage increases home expectation."
                    : "Home red-card disadvantage increases away expectation.");
        }
        if (nonZero(features.xgDelta()) || nonZero(features.shotPressureDelta())
                || nonZero(features.fieldTilt()) || nonZero(features.momentumTrend())) {
            explanations.add("Live pressure features shift attacking expectation toward the stronger recent side.");
        }
        if (features.coverageMode() != CoverageMode.RICH) {
            explanations.add("Some rich event features are missing, so confidence is reduced.");
        }
        if (features.providerProbability() != null) {
            double divergence = providerDivergence(probability, features.providerProbability()) * 100.0;
            explanations.add("Provider probability is used for comparison only; maximum divergence is "
                    + rounded(divergence) + " percentage points.");
        }
        return explanations;
    }

    private double providerDivergence(Probability model, Probability provider) {
        return Math.max(
                Math.abs(model.homeWin() - provider.homeWin()),
                Math.max(Math.abs(model.draw() - provider.draw()), Math.abs(model.awayWin() - provider.awayWin()))
        );
    }

    private boolean nonZero(Double value) {
        return value != null && Double.isFinite(value) && Math.abs(value) > 0.000_1;
    }

    private double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
