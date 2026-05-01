package com.example.sportsanalytics.application.backtest;

import java.util.UUID;

public class BacktestRunNotFoundException extends RuntimeException {
    public BacktestRunNotFoundException(UUID runId) {
        super("Backtest run not found: " + runId);
    }
}
