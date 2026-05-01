package com.example.sportsanalytics.analytics.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BacktestMetricsCalculator {
    public static final String EVALUATION_VERSION = "stage5.5-v1";

    private static final double LOG_LOSS_EPSILON = 1e-15;
    private static final int MAX_REMAINING_GOALS = 8;
    private static final List<TargetMinute> TARGET_MINUTES = List.of(
            new TargetMinute("0", 0),
            new TargetMinute("15", 15),
            new TargetMinute("30", 30),
            new TargetMinute("HT", 45),
            new TargetMinute("60", 60),
            new TargetMinute("75", 75),
            new TargetMinute("85", 85)
    );
    private static final List<MinuteBucket> MINUTE_BUCKETS = List.of(
            new MinuteBucket("0-15", 0, 15),
            new MinuteBucket("16-30", 16, 30),
            new MinuteBucket("31-45", 31, 45),
            new MinuteBucket("46-60", 46, 60),
            new MinuteBucket("61-75", 61, 75),
            new MinuteBucket("76-85", 76, 85),
            new MinuteBucket("86-89", 86, 89)
    );

    public BacktestMetrics calculate(List<BacktestMatchSample> matches) {
        List<BacktestMatchSample> usableMatches = matches == null ? List.of() : matches.stream()
                .filter(match -> !match.probabilities().isEmpty())
                .toList();
        if (usableMatches.isEmpty()) {
            return emptyMetrics();
        }

        Map<TargetMinute, List<EvaluationSample>> fixedSamplesByMinute = new LinkedHashMap<>();
        TARGET_MINUTES.forEach(target -> fixedSamplesByMinute.put(target, new ArrayList<>()));
        List<EvaluationSample> headlineSamples = new ArrayList<>();
        List<EvaluationSample> allInPlaySamples = new ArrayList<>();
        List<EvaluationSample> finalSamples = new ArrayList<>();
        List<EvaluationSample> randomBaselineSamples = new ArrayList<>();
        List<EvaluationSample> scoreOnlyBaselineSamples = new ArrayList<>();
        List<EvaluationSample> providerBaselineSamples = new ArrayList<>();
        List<BacktestMatchMetrics> matchMetrics = new ArrayList<>();
        Map<String, MovementAccumulator> movements = new LinkedHashMap<>();

        for (BacktestMatchSample match : usableMatches) {
            List<BacktestProbabilitySample> probabilities = ordered(match.probabilities());
            List<EvaluationSample> fixedSamplesForMatch = new ArrayList<>();
            for (TargetMinute target : TARGET_MINUTES) {
                BacktestProbabilitySample sample = latestAtOrBefore(probabilities, target.minute());
                if (sample == null) {
                    continue;
                }
                EvaluationSample evaluationSample = new EvaluationSample(match.matchId(), match.actualOutcome(), sample);
                fixedSamplesByMinute.get(target).add(evaluationSample);
                headlineSamples.add(evaluationSample);
                fixedSamplesForMatch.add(evaluationSample);
                randomBaselineSamples.add(new EvaluationSample(
                        match.matchId(),
                        match.actualOutcome(),
                        sample.withProbabilities(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0)
                ));
                scoreOnlyBaselineSamples.add(new EvaluationSample(
                        match.matchId(),
                        match.actualOutcome(),
                        scoreOnlyBaseline(sample)
                ));
            }

            BacktestProbabilitySample provider = firstProviderProbability(probabilities);
            if (provider != null) {
                for (EvaluationSample fixedSample : fixedSamplesForMatch) {
                    providerBaselineSamples.add(new EvaluationSample(
                            match.matchId(),
                            match.actualOutcome(),
                            fixedSample.probability().withProbabilities(
                                    provider.providerHomeWin(),
                                    provider.providerDraw(),
                                    provider.providerAwayWin()
                            )
                    ));
                }
            }

            probabilities.stream()
                    .filter(probability -> probability.minute() < 90)
                    .map(probability -> new EvaluationSample(match.matchId(), match.actualOutcome(), probability))
                    .forEach(allInPlaySamples::add);

            BacktestProbabilitySample previous = null;
            for (BacktestProbabilitySample current : probabilities) {
                if (previous != null) {
                    String type = current.eventType() == null || current.eventType().isBlank()
                            ? "UNKNOWN"
                            : current.eventType();
                    movements.computeIfAbsent(type, ignored -> new MovementAccumulator())
                            .add(movement(previous, current));
                }
                previous = current;
            }

            BacktestProbabilitySample finalSnapshot = probabilities.get(probabilities.size() - 1);
            finalSamples.add(new EvaluationSample(match.matchId(), match.actualOutcome(), finalSnapshot));
            EvaluationMetricSummary matchHeadline = summary(fixedSamplesForMatch);
            matchMetrics.add(new BacktestMatchMetrics(
                    match.matchId(),
                    match.providerMatchId(),
                    match.actualOutcome(),
                    probabilities.size(),
                    (int) probabilities.stream().filter(probability -> probability.minute() < 90).count(),
                    fixedSamplesForMatch.size(),
                    matchHeadline.brierScore(),
                    matchHeadline.logLoss(),
                    topPick(finalSnapshot) == match.actualOutcome()
            ));
        }

        List<FixedMinuteMetric> fixedMinuteMetrics = new ArrayList<>();
        fixedSamplesByMinute.forEach((target, samples) -> fixedMinuteMetrics.add(new FixedMinuteMetric(
                target.label(),
                target.minute(),
                summary(samples)
        )));

        Map<String, Double> movementAverages = new LinkedHashMap<>();
        movements.forEach((type, accumulator) -> movementAverages.put(type, accumulator.average()));

        return new BacktestMetrics(
                EVALUATION_VERSION,
                summary(headlineSamples),
                fixedMinuteMetrics,
                summary(allInPlaySamples),
                finalDiagnostic(finalSamples),
                new BaselineMetrics(
                        summary(randomBaselineSamples),
                        summary(scoreOnlyBaselineSamples),
                        summary(providerBaselineSamples)
                ),
                minuteBucketCalibration(allInPlaySamples),
                movementAverages,
                matchMetrics
        );
    }

    private BacktestMetrics emptyMetrics() {
        EvaluationMetricSummary emptySummary = new EvaluationMetricSummary(0, 0, 0.0, 0.0, 0.0);
        List<FixedMinuteMetric> fixedMinuteMetrics = TARGET_MINUTES.stream()
                .map(target -> new FixedMinuteMetric(target.label(), target.minute(), emptySummary))
                .toList();
        return new BacktestMetrics(
                EVALUATION_VERSION,
                emptySummary,
                fixedMinuteMetrics,
                emptySummary,
                new FinalSnapshotDiagnostic(0, 0.0, 0.0, 0.0),
                new BaselineMetrics(emptySummary, emptySummary, emptySummary),
                minuteBucketCalibration(List.of()),
                Map.of(),
                List.of()
        );
    }

    private List<BacktestProbabilitySample> ordered(List<BacktestProbabilitySample> probabilities) {
        return probabilities.stream()
                .sorted(Comparator
                        .comparingInt(BacktestProbabilitySample::minute)
                        .thenComparing(probability -> probability.eventSequence() == null
                                ? Long.MAX_VALUE
                                : probability.eventSequence()))
                .toList();
    }

    private BacktestProbabilitySample latestAtOrBefore(List<BacktestProbabilitySample> probabilities, int minute) {
        BacktestProbabilitySample latest = null;
        for (BacktestProbabilitySample probability : probabilities) {
            if (probability.minute() <= minute) {
                latest = probability;
            }
        }
        return latest;
    }

    private BacktestProbabilitySample firstProviderProbability(List<BacktestProbabilitySample> probabilities) {
        return probabilities.stream()
                .filter(BacktestProbabilitySample::hasProviderProbability)
                .findFirst()
                .orElse(null);
    }

    private EvaluationMetricSummary summary(List<EvaluationSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return new EvaluationMetricSummary(0, 0, 0.0, 0.0, 0.0);
        }
        Set<UUID> matches = new HashSet<>();
        double brierTotal = 0.0;
        double logLossTotal = 0.0;
        int correct = 0;
        for (EvaluationSample sample : samples) {
            matches.add(sample.matchId());
            brierTotal += brier(sample.probability(), sample.actualOutcome());
            logLossTotal += logLoss(sample.probability(), sample.actualOutcome());
            if (topPick(sample.probability()) == sample.actualOutcome()) {
                correct++;
            }
        }
        return new EvaluationMetricSummary(
                matches.size(),
                samples.size(),
                brierTotal / samples.size(),
                logLossTotal / samples.size(),
                correct / (double) samples.size()
        );
    }

    private FinalSnapshotDiagnostic finalDiagnostic(List<EvaluationSample> samples) {
        EvaluationMetricSummary summary = summary(samples);
        return new FinalSnapshotDiagnostic(
                summary.matchCount(),
                summary.brierScore(),
                summary.logLoss(),
                summary.topPickAccuracy()
        );
    }

    private List<MinuteBucketCalibration> minuteBucketCalibration(List<EvaluationSample> samples) {
        List<MinuteBucketCalibration> values = new ArrayList<>();
        for (MinuteBucket bucket : MINUTE_BUCKETS) {
            List<EvaluationSample> bucketSamples = samples.stream()
                    .filter(sample -> sample.probability().minute() >= bucket.startMinute()
                            && sample.probability().minute() <= bucket.endMinute())
                    .toList();
            values.add(calibration(bucket.label(), bucketSamples));
        }
        return values;
    }

    private MinuteBucketCalibration calibration(String label, List<EvaluationSample> samples) {
        if (samples.isEmpty()) {
            return new MinuteBucketCalibration(label, 0, 0.0, 0.0);
        }
        double confidenceTotal = 0.0;
        int correct = 0;
        for (EvaluationSample sample : samples) {
            confidenceTotal += confidence(sample.probability());
            if (topPick(sample.probability()) == sample.actualOutcome()) {
                correct++;
            }
        }
        return new MinuteBucketCalibration(
                label,
                samples.size(),
                confidenceTotal / samples.size(),
                correct / (double) samples.size()
        );
    }

    private BacktestProbabilitySample scoreOnlyBaseline(BacktestProbabilitySample sample) {
        double timeRemainingRatio = Math.max(0.0, Math.min(1.0, (90 - sample.minute()) / 90.0));
        ProbabilityVector probability = simulateOutcomeProbability(
                sample.homeScore(),
                sample.awayScore(),
                1.25 * timeRemainingRatio,
                1.10 * timeRemainingRatio
        );
        return sample.withProbabilities(probability.homeWin(), probability.draw(), probability.awayWin());
    }

    private ProbabilityVector simulateOutcomeProbability(
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
            return new ProbabilityVector(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0);
        }
        return new ProbabilityVector(homeWin / total, draw / total, awayWin / total);
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

    private double brier(BacktestProbabilitySample probability, Outcome actualOutcome) {
        return Math.pow(probability.homeWin() - target(actualOutcome, Outcome.HOME_WIN), 2.0)
                + Math.pow(probability.draw() - target(actualOutcome, Outcome.DRAW), 2.0)
                + Math.pow(probability.awayWin() - target(actualOutcome, Outcome.AWAY_WIN), 2.0);
    }

    private double logLoss(BacktestProbabilitySample probability, Outcome actualOutcome) {
        return -Math.log(Math.max(LOG_LOSS_EPSILON, actualProbability(probability, actualOutcome)));
    }

    private double actualProbability(BacktestProbabilitySample probability, Outcome actualOutcome) {
        return switch (actualOutcome) {
            case HOME_WIN -> probability.homeWin();
            case DRAW -> probability.draw();
            case AWAY_WIN -> probability.awayWin();
        };
    }

    private double target(Outcome actualOutcome, Outcome candidate) {
        return actualOutcome == candidate ? 1.0 : 0.0;
    }

    private Outcome topPick(BacktestProbabilitySample probability) {
        if (probability.homeWin() >= probability.draw() && probability.homeWin() >= probability.awayWin()) {
            return Outcome.HOME_WIN;
        }
        if (probability.draw() >= probability.awayWin()) {
            return Outcome.DRAW;
        }
        return Outcome.AWAY_WIN;
    }

    private double confidence(BacktestProbabilitySample probability) {
        return Math.max(probability.homeWin(), Math.max(probability.draw(), probability.awayWin()));
    }

    private double movement(BacktestProbabilitySample previous, BacktestProbabilitySample current) {
        return Math.abs(current.homeWin() - previous.homeWin())
                + Math.abs(current.draw() - previous.draw())
                + Math.abs(current.awayWin() - previous.awayWin());
    }

    private record TargetMinute(String label, int minute) {
    }

    private record MinuteBucket(String label, int startMinute, int endMinute) {
    }

    private record EvaluationSample(UUID matchId, Outcome actualOutcome, BacktestProbabilitySample probability) {
    }

    private record ProbabilityVector(double homeWin, double draw, double awayWin) {
    }

    private static final class MovementAccumulator {
        private int count;
        private double total;

        private void add(double value) {
            count++;
            total += value;
        }

        private double average() {
            return count == 0 ? 0.0 : total / count;
        }
    }
}
