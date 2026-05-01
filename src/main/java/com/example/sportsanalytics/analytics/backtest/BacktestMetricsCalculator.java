package com.example.sportsanalytics.analytics.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BacktestMetricsCalculator {
    private static final double LOG_LOSS_EPSILON = 1e-15;

    public BacktestMetrics calculate(List<BacktestMatchSample> matches) {
        List<BacktestMatchSample> usableMatches = matches == null ? List.of() : matches.stream()
                .filter(match -> !match.probabilities().isEmpty())
                .toList();
        if (usableMatches.isEmpty()) {
            return new BacktestMetrics(0, 0, 0.0, 0.0, 0.0, emptyBuckets(), Map.of(), List.of());
        }

        List<BacktestMatchMetrics> matchMetrics = new ArrayList<>();
        BucketAccumulator[] buckets = buckets();
        Map<String, MovementAccumulator> movements = new LinkedHashMap<>();
        double brierTotal = 0.0;
        double logLossTotal = 0.0;
        int snapshotCount = 0;
        int correctFinalPicks = 0;

        for (BacktestMatchSample match : usableMatches) {
            double matchBrierTotal = 0.0;
            double matchLogLossTotal = 0.0;
            BacktestProbabilitySample previous = null;
            for (BacktestProbabilitySample probability : match.probabilities()) {
                double brier = brier(probability, match.actualOutcome());
                double logLoss = logLoss(probability, match.actualOutcome());
                brierTotal += brier;
                logLossTotal += logLoss;
                matchBrierTotal += brier;
                matchLogLossTotal += logLoss;
                snapshotCount++;
                addCalibration(buckets, probability, match.actualOutcome());
                if (previous != null) {
                    String type = probability.eventType() == null || probability.eventType().isBlank()
                            ? "UNKNOWN"
                            : probability.eventType();
                    movements.computeIfAbsent(type, ignored -> new MovementAccumulator())
                            .add(movement(previous, probability));
                }
                previous = probability;
            }

            BacktestProbabilitySample finalSnapshot = match.probabilities().get(match.probabilities().size() - 1);
            boolean correct = topPick(finalSnapshot) == match.actualOutcome();
            if (correct) {
                correctFinalPicks++;
            }
            matchMetrics.add(new BacktestMatchMetrics(
                    match.matchId(),
                    match.providerMatchId(),
                    match.actualOutcome(),
                    match.probabilities().size(),
                    matchBrierTotal / match.probabilities().size(),
                    matchLogLossTotal / match.probabilities().size(),
                    correct
            ));
        }

        Map<String, Double> movementAverages = new LinkedHashMap<>();
        movements.forEach((type, accumulator) -> movementAverages.put(type, accumulator.average()));
        return new BacktestMetrics(
                usableMatches.size(),
                snapshotCount,
                brierTotal / snapshotCount,
                logLossTotal / snapshotCount,
                correctFinalPicks / (double) usableMatches.size(),
                calibrationBuckets(buckets),
                movementAverages,
                matchMetrics
        );
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

    private void addCalibration(BucketAccumulator[] buckets, BacktestProbabilitySample probability, Outcome actualOutcome) {
        double confidence = Math.max(probability.homeWin(), Math.max(probability.draw(), probability.awayWin()));
        int index = Math.min(9, Math.max(0, (int) Math.floor(confidence * 10.0)));
        buckets[index].add(confidence, topPick(probability) == actualOutcome);
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

    private double movement(BacktestProbabilitySample previous, BacktestProbabilitySample current) {
        return Math.abs(current.homeWin() - previous.homeWin())
                + Math.abs(current.draw() - previous.draw())
                + Math.abs(current.awayWin() - previous.awayWin());
    }

    private BucketAccumulator[] buckets() {
        BucketAccumulator[] buckets = new BucketAccumulator[10];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new BucketAccumulator(bucketName(i));
        }
        return buckets;
    }

    private List<CalibrationBucket> emptyBuckets() {
        return calibrationBuckets(buckets());
    }

    private List<CalibrationBucket> calibrationBuckets(BucketAccumulator[] buckets) {
        List<CalibrationBucket> values = new ArrayList<>();
        for (BucketAccumulator bucket : buckets) {
            values.add(bucket.toBucket());
        }
        return values;
    }

    private String bucketName(int index) {
        return String.format("%.1f-%.1f", index / 10.0, (index + 1) / 10.0);
    }

    private static final class BucketAccumulator {
        private final String name;
        private int count;
        private double confidenceTotal;
        private int correct;

        private BucketAccumulator(String name) {
            this.name = name;
        }

        private void add(double confidence, boolean correctPick) {
            count++;
            confidenceTotal += confidence;
            if (correctPick) {
                correct++;
            }
        }

        private CalibrationBucket toBucket() {
            return new CalibrationBucket(
                    name,
                    count,
                    count == 0 ? 0.0 : confidenceTotal / count,
                    count == 0 ? 0.0 : correct / (double) count
            );
        }
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
