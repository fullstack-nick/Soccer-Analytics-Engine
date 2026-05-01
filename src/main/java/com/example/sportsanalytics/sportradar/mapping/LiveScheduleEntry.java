package com.example.sportsanalytics.sportradar.mapping;

public record LiveScheduleEntry(
        String providerMatchId,
        String status,
        String matchStatus
) {
    public boolean ended() {
        String combined = ((status == null ? "" : status) + " " + (matchStatus == null ? "" : matchStatus)).toLowerCase();
        return combined.contains("closed")
                || combined.contains("ended")
                || combined.contains("complete")
                || combined.contains("finished");
    }
}
