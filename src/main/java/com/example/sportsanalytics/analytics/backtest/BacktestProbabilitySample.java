package com.example.sportsanalytics.analytics.backtest;

public record BacktestProbabilitySample(
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
    public boolean hasProviderProbability() {
        return providerHomeWin != null && providerDraw != null && providerAwayWin != null;
    }

    public BacktestProbabilitySample withProbabilities(double homeWin, double draw, double awayWin) {
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
