package com.example.sportsanalytics.sportradar.client;

import java.util.List;

public enum SportradarEndpoint {
    SPORT_EVENT_SUMMARY("summary"),
    SPORT_EVENT_TIMELINE("timeline"),
    SPORT_EVENT_EXTENDED_TIMELINE("extended_timeline"),
    SPORT_EVENT_LINEUPS("lineups"),
    SPORT_EVENT_MOMENTUM("momentum"),
    SPORT_EVENT_EXTENDED_SUMMARY("extended_summary"),
    SEASON_INFO("season_info"),
    SEASON_STANDINGS("season_standings"),
    SEASON_FORM_STANDINGS("season_form_standings"),
    SEASON_PROBABILITIES("season_probabilities"),
    SEASON_SCHEDULES("season_schedules"),
    LIVE_SCHEDULES("live_schedules"),
    LIVE_TIMELINES("live_timelines"),
    LIVE_TIMELINES_DELTA("live_timelines_delta");

    private final String sourceEndpoint;

    SportradarEndpoint(String sourceEndpoint) {
        this.sourceEndpoint = sourceEndpoint;
    }

    public String sourceEndpoint() {
        return sourceEndpoint;
    }

    public List<String> pathSegments(String providerId) {
        return switch (this) {
            case SPORT_EVENT_SUMMARY -> List.of("sport_events", providerId, "summary");
            case SPORT_EVENT_TIMELINE -> List.of("sport_events", providerId, "timeline");
            case SPORT_EVENT_EXTENDED_TIMELINE -> List.of("sport_events", providerId, "extended_timeline");
            case SPORT_EVENT_LINEUPS -> List.of("sport_events", providerId, "lineups");
            case SPORT_EVENT_MOMENTUM -> List.of("sport_events", providerId, "momentum");
            case SPORT_EVENT_EXTENDED_SUMMARY -> List.of("sport_events", providerId, "extended_summary");
            case SEASON_INFO -> List.of("seasons", providerId, "info");
            case SEASON_STANDINGS -> List.of("seasons", providerId, "standings");
            case SEASON_FORM_STANDINGS -> List.of("seasons", providerId, "form_standings");
            case SEASON_PROBABILITIES -> List.of("seasons", providerId, "probabilities");
            case SEASON_SCHEDULES -> List.of("seasons", providerId, "schedules");
            case LIVE_SCHEDULES -> List.of("schedules", "live", "schedules");
            case LIVE_TIMELINES -> List.of("schedules", "live", "timelines");
            case LIVE_TIMELINES_DELTA -> List.of("schedules", "live", "timelines_delta");
        };
    }
}
