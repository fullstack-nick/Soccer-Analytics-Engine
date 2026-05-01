package com.example.sportsanalytics.application.backtest.dto;

public record BacktestFailureView(
        String sportEventId,
        String message
) {
}
