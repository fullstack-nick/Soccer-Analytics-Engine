package com.example.sportsanalytics.domain.model;

public record Probability(double homeWin, double draw, double awayWin) {
    private static final double SUM_TOLERANCE = 0.000_001;

    public Probability {
        validateProbability("homeWin", homeWin);
        validateProbability("draw", draw);
        validateProbability("awayWin", awayWin);
        double sum = homeWin + draw + awayWin;
        if (Math.abs(1.0 - sum) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("probabilities must sum to 1.0");
        }
    }

    private static void validateProbability(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }
}
