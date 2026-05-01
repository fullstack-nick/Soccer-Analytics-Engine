package com.example.sportsanalytics.application.match.dto;

public record EventWriteCounts(int inserted, int updated) {
    public int changed() {
        return inserted + updated;
    }
}
