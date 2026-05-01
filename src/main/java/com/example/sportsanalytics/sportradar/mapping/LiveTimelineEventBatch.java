package com.example.sportsanalytics.sportradar.mapping;

import com.example.sportsanalytics.domain.model.TimelineSourceType;
import java.util.List;
import java.util.UUID;

public record LiveTimelineEventBatch(
        String providerMatchId,
        TimelineSourceType sourceType,
        UUID rawPayloadId,
        List<NormalizedTimelineEvent> events
) {
    public LiveTimelineEventBatch {
        events = List.copyOf(events == null ? List.of() : events);
    }
}
