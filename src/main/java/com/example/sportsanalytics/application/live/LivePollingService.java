package com.example.sportsanalytics.application.live;

import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.live.dto.LivePollResult;
import com.example.sportsanalytics.application.match.MatchEventIngestionService;
import com.example.sportsanalytics.application.match.MatchStateRebuildService;
import com.example.sportsanalytics.application.match.dto.EventWriteCounts;
import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.LiveTrackingStatus;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.LiveMatchTrackingEntity;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.LiveMatchTrackingRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.example.sportsanalytics.sportradar.mapping.LiveScheduleEntry;
import com.example.sportsanalytics.sportradar.mapping.LiveSportradarMapper;
import com.example.sportsanalytics.sportradar.mapping.LiveTimelineEventBatch;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LivePollingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LivePollingService.class);
    private static final String LIVE_PROVIDER_ID = "live";

    private final SportsAnalyticsProperties properties;
    private final SportradarClient sportradarClient;
    private final LiveSportradarMapper liveMapper;
    private final LiveMatchTrackingRepository liveTrackingRepository;
    private final MatchEventIngestionService eventIngestionService;
    private final MatchStateRebuildService rebuildService;
    private final AlertGenerationService alertGenerationService;
    private final EntityManager entityManager;
    private final Clock clock;
    private Instant lastFullTimelineFetch = Instant.EPOCH;

    @Autowired
    public LivePollingService(
            SportsAnalyticsProperties properties,
            SportradarClient sportradarClient,
            LiveSportradarMapper liveMapper,
            LiveMatchTrackingRepository liveTrackingRepository,
            MatchEventIngestionService eventIngestionService,
            MatchStateRebuildService rebuildService,
            AlertGenerationService alertGenerationService,
            EntityManager entityManager
    ) {
        this(properties, sportradarClient, liveMapper, liveTrackingRepository, eventIngestionService, rebuildService,
                alertGenerationService, entityManager, Clock.systemUTC());
    }

    LivePollingService(
            SportsAnalyticsProperties properties,
            SportradarClient sportradarClient,
            LiveSportradarMapper liveMapper,
            LiveMatchTrackingRepository liveTrackingRepository,
            MatchEventIngestionService eventIngestionService,
            MatchStateRebuildService rebuildService,
            AlertGenerationService alertGenerationService,
            EntityManager entityManager,
            Clock clock
    ) {
        this.properties = properties;
        this.sportradarClient = sportradarClient;
        this.liveMapper = liveMapper;
        this.liveTrackingRepository = liveTrackingRepository;
        this.eventIngestionService = eventIngestionService;
        this.rebuildService = rebuildService;
        this.alertGenerationService = alertGenerationService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public LivePollResult pollOnce() {
        if (!properties.getLive().isEnabled()) {
            return new LivePollResult(0, 0, 0, 0, 0, 0);
        }
        List<LiveMatchTrackingEntity> active = liveTrackingRepository
                .findByActiveTrueOrderByUpdatedAtAsc()
                .stream()
                .limit(properties.getLive().getMaxMatchesPerTick())
                .toList();
        if (active.isEmpty()) {
            return new LivePollResult(0, 0, 0, 0, 0, 0);
        }

        Instant now = clock.instant();
        SportradarPayload liveSchedules;
        SportradarPayload delta;
        try {
            liveSchedules = sportradarClient.fetch(SportradarEndpoint.LIVE_SCHEDULES, LIVE_PROVIDER_ID, true);
            delta = sportradarClient.fetch(SportradarEndpoint.LIVE_TIMELINES_DELTA, LIVE_PROVIDER_ID, true);
        } catch (RuntimeException exception) {
            active.forEach(tracking -> markFailure(tracking, now, exception));
            return new LivePollResult(active.size(), 0, 0, 0, 0, 0);
        }

        Map<String, LiveScheduleEntry> scheduleByProvider = liveMapper.schedules(liveSchedules.payload()).stream()
                .collect(Collectors.toMap(
                        LiveScheduleEntry::providerMatchId,
                        entry -> entry,
                        (first, second) -> second,
                        LinkedHashMap::new
                ));

        Map<String, List<LiveTimelineEventBatch>> batchesByProvider = new LinkedHashMap<>();
        addBatches(
                batchesByProvider,
                liveMapper.timelineBatches(delta.payload(), TimelineSourceType.LIVE_DELTA, delta.rawPayloadId())
        );

        SportradarPayload fullTimeline = null;
        if (shouldFetchFullTimeline(now)) {
            try {
                fullTimeline = sportradarClient.fetch(SportradarEndpoint.LIVE_TIMELINES, LIVE_PROVIDER_ID, true);
                addBatches(
                        batchesByProvider,
                        liveMapper.timelineBatches(
                                fullTimeline.payload(),
                                TimelineSourceType.LIVE_TIMELINE,
                                fullTimeline.rawPayloadId()
                        )
                );
                lastFullTimelineFetch = now;
            } catch (RuntimeException exception) {
                active.forEach(tracking -> markFailure(tracking, now, exception));
            }
        }

        int processed = 0;
        int inserted = 0;
        int updated = 0;
        int ended = 0;
        int alerts = 0;
        for (LiveMatchTrackingEntity tracking : active) {
            try {
                List<LiveTimelineEventBatch> matchBatches = new ArrayList<>(
                        batchesByProvider.getOrDefault(tracking.getProviderMatchId(), List.of())
                );
                RichRefreshResult richRefresh = richRefresh(tracking, now);
                matchBatches.addAll(richRefresh.batches());
                PollMatchResult result = processTracking(tracking, matchBatches, now);
                inserted += result.inserted();
                updated += result.updated();
                alerts += result.alertsCreated();
                processed++;
                tracking.setLastDeltaPayload(rawPayloadReference(delta.rawPayloadId()));
                if (fullTimeline != null) {
                    tracking.setLastFullTimelinePayload(rawPayloadReference(fullTimeline.rawPayloadId()));
                }
                if (richRefresh.payloadId() != null) {
                    tracking.setLastRichTimelinePayload(rawPayloadReference(richRefresh.payloadId()));
                    tracking.setLastRichTimelineRefreshAt(now);
                }
                LiveScheduleEntry schedule = scheduleByProvider.get(tracking.getProviderMatchId());
                if (schedule != null && schedule.ended()) {
                    tracking.setTrackingStatus(LiveTrackingStatus.ENDED);
                    tracking.setActive(false);
                    tracking.setStoppedAt(now);
                    ended++;
                } else {
                    tracking.setTrackingStatus(LiveTrackingStatus.TRACKING);
                    tracking.setActive(true);
                }
                markSuccess(tracking, now);
            } catch (RuntimeException exception) {
                markFailure(tracking, now, exception);
            }
        }
        return new LivePollResult(active.size(), processed, inserted, updated, ended, alerts);
    }

    private RichRefreshResult richRefresh(LiveMatchTrackingEntity tracking, Instant now) {
        if (!shouldFetchRichTimeline(tracking, now)) {
            return RichRefreshResult.empty();
        }
        try {
            SportradarPayload payload = sportradarClient.fetch(
                    SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE,
                    tracking.getProviderMatchId(),
                    true
            );
            List<LiveTimelineEventBatch> batches = liveMapper
                    .timelineBatches(payload.payload(), TimelineSourceType.EXTENDED, payload.rawPayloadId())
                    .stream()
                    .filter(batch -> tracking.getProviderMatchId().equals(batch.providerMatchId()))
                    .toList();
            return new RichRefreshResult(payload.rawPayloadId(), batches);
        } catch (RuntimeException exception) {
            LOGGER.debug("Live rich timeline refresh skipped for {}: {}", tracking.getProviderMatchId(), exception.getMessage());
            return RichRefreshResult.empty();
        }
    }

    private PollMatchResult processTracking(
            LiveMatchTrackingEntity tracking,
            List<LiveTimelineEventBatch> batches,
            Instant now
    ) {
        int inserted = 0;
        int updated = 0;
        if (batches != null) {
            for (LiveTimelineEventBatch batch : batches) {
                EventWriteCounts counts = eventIngestionService.upsertEvents(tracking.getMatch(), batch.events());
                inserted += counts.inserted();
                updated += counts.updated();
            }
        }
        int alerts = 0;
        if (inserted + updated > 0) {
            rebuildService.rebuild(tracking.getMatch().getId());
            alerts = alertGenerationService.generate(tracking.getMatch().getId());
        }
        tracking.setLastPollAt(now);
        return new PollMatchResult(inserted, updated, alerts);
    }

    private void addBatches(
            Map<String, List<LiveTimelineEventBatch>> target,
            List<LiveTimelineEventBatch> batches
    ) {
        for (LiveTimelineEventBatch batch : batches) {
            target.computeIfAbsent(batch.providerMatchId(), ignored -> new ArrayList<>()).add(batch);
        }
    }

    private boolean shouldFetchFullTimeline(Instant now) {
        return Duration.between(lastFullTimelineFetch, now).toMillis()
                >= properties.getLive().getFullTimelineRefreshMs();
    }

    private boolean shouldFetchRichTimeline(LiveMatchTrackingEntity tracking, Instant now) {
        if (!properties.getLive().isRichRefreshEnabled()
                || tracking.getMatch().getCoverageMode() != CoverageMode.RICH) {
            return false;
        }
        Instant lastRefresh = tracking.getLastRichTimelineRefreshAt();
        if (lastRefresh == null && tracking.getLastRichTimelinePayload() != null) {
            lastRefresh = tracking.getLastRichTimelinePayload().getFetchedAt();
        }
        return lastRefresh == null
                || Duration.between(lastRefresh, now).toMillis() >= properties.getLive().getRichRefreshMs();
    }

    private void markSuccess(LiveMatchTrackingEntity tracking, Instant now) {
        tracking.setLastPollAt(now);
        tracking.setLastSuccessAt(now);
        tracking.setLastError(null);
        tracking.setUpdatedAt(now);
        liveTrackingRepository.save(tracking);
    }

    private void markFailure(LiveMatchTrackingEntity tracking, Instant now, RuntimeException exception) {
        tracking.setTrackingStatus(LiveTrackingStatus.ERROR);
        tracking.setLastPollAt(now);
        tracking.setLastErrorAt(now);
        tracking.setErrorCount(tracking.getErrorCount() + 1);
        tracking.setLastError(trimError(exception.getMessage()));
        tracking.setUpdatedAt(now);
        liveTrackingRepository.save(tracking);
    }

    private RawPayloadEntity rawPayloadReference(UUID rawPayloadId) {
        if (rawPayloadId == null) {
            return null;
        }
        return entityManager.getReference(RawPayloadEntity.class, rawPayloadId);
    }

    private String trimError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record PollMatchResult(int inserted, int updated, int alertsCreated) {
    }

    private record RichRefreshResult(UUID payloadId, List<LiveTimelineEventBatch> batches) {
        private RichRefreshResult {
            batches = List.copyOf(batches == null ? List.of() : batches);
        }

        static RichRefreshResult empty() {
            return new RichRefreshResult(null, List.of());
        }
    }
}
