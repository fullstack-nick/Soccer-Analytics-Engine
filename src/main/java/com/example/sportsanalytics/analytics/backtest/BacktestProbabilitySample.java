package com.example.sportsanalytics.analytics.backtest;

public record BacktestProbabilitySample(
        Long eventSequence,
        String eventType,
        double homeWin,
        double draw,
        double awayWin
) {
}
