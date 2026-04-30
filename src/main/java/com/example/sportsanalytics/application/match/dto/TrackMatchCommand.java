package com.example.sportsanalytics.application.match.dto;

public record TrackMatchCommand(String sportEventId, boolean forceRefresh) {
}
