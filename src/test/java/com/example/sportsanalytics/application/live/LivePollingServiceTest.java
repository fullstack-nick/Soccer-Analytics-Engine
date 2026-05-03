package com.example.sportsanalytics.application.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.live.dto.LivePollResult;
import com.example.sportsanalytics.application.match.MatchEventIngestionService;
import com.example.sportsanalytics.application.match.MatchStateRebuildService;
import com.example.sportsanalytics.application.match.dto.EventWriteCounts;
import com.example.sportsanalytics.application.match.dto.RebuildMatchStateResult;
import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.LiveMatchTrackingEntity;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.LiveMatchTrackingRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.example.sportsanalytics.sportradar.mapping.LiveScheduleEntry;
import com.example.sportsanalytics.sportradar.mapping.LiveSportradarMapper;
import com.example.sportsanalytics.sportradar.mapping.LiveTimelineEventBatch;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivePollingServiceTest {
    private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PROVIDER_MATCH_ID = "sr:sport_event:61526558";
    private static final Instant NOW = Instant.parse("2026-05-03T14:30:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pollOnceDoesNothingWhenLivePollingIsDisabled() {
        SportsAnalyticsProperties properties = new SportsAnalyticsProperties();
        SportradarClient sportradarClient = mock(SportradarClient.class);
        LiveMatchTrackingRepository trackingRepository = mock(LiveMatchTrackingRepository.class);
        LivePollingService service = new LivePollingService(
                properties,
                sportradarClient,
                mock(LiveSportradarMapper.class),
                trackingRepository,
                mock(MatchEventIngestionService.class),
                mock(MatchStateRebuildService.class),
                mock(AlertGenerationService.class),
                mock(EntityManager.class),
                Clock.systemUTC()
        );

        LivePollResult result = service.pollOnce();

        assertThat(result.registeredMatches()).isZero();
        verifyNoInteractions(sportradarClient, trackingRepository);
    }

    @Test
    void pollOnceRefreshesExtendedTimelineForRichTrackedMatches() {
        SportsAnalyticsProperties properties = new SportsAnalyticsProperties();
        properties.getLive().setEnabled(true);
        properties.getLive().setMaxMatchesPerTick(1);
        properties.getLive().setRichRefreshEnabled(true);
        properties.getLive().setRichRefreshMs(10_000);
        SportradarClient sportradarClient = mock(SportradarClient.class);
        LiveSportradarMapper liveMapper = mock(LiveSportradarMapper.class);
        LiveMatchTrackingRepository trackingRepository = mock(LiveMatchTrackingRepository.class);
        MatchEventIngestionService eventIngestionService = mock(MatchEventIngestionService.class);
        MatchStateRebuildService rebuildService = mock(MatchStateRebuildService.class);
        AlertGenerationService alertGenerationService = mock(AlertGenerationService.class);
        EntityManager entityManager = mock(EntityManager.class);
        LivePollingService service = new LivePollingService(
                properties,
                sportradarClient,
                liveMapper,
                trackingRepository,
                eventIngestionService,
                rebuildService,
                alertGenerationService,
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        MatchEntity match = match(CoverageMode.RICH);
        LiveMatchTrackingEntity tracking = tracking(match);
        SportradarPayload schedules = payload(SportradarEndpoint.LIVE_SCHEDULES, "live");
        SportradarPayload delta = payload(SportradarEndpoint.LIVE_TIMELINES_DELTA, "live");
        SportradarPayload liveTimeline = payload(SportradarEndpoint.LIVE_TIMELINES, "live");
        SportradarPayload richTimeline = payload(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE, PROVIDER_MATCH_ID);
        NormalizedTimelineEvent xgEvent = xgEvent(richTimeline.rawPayloadId());
        LiveTimelineEventBatch richBatch = new LiveTimelineEventBatch(
                PROVIDER_MATCH_ID,
                TimelineSourceType.EXTENDED,
                richTimeline.rawPayloadId(),
                List.of(xgEvent)
        );
        RawPayloadEntity rawPayload = new RawPayloadEntity();
        when(trackingRepository.findByActiveTrueOrderByUpdatedAtAsc()).thenReturn(List.of(tracking));
        when(sportradarClient.fetch(SportradarEndpoint.LIVE_SCHEDULES, "live", true)).thenReturn(schedules);
        when(sportradarClient.fetch(SportradarEndpoint.LIVE_TIMELINES_DELTA, "live", true)).thenReturn(delta);
        when(sportradarClient.fetch(SportradarEndpoint.LIVE_TIMELINES, "live", true)).thenReturn(liveTimeline);
        when(sportradarClient.fetch(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE, PROVIDER_MATCH_ID, true))
                .thenReturn(richTimeline);
        when(liveMapper.schedules(schedules.payload()))
                .thenReturn(List.of(new LiveScheduleEntry(PROVIDER_MATCH_ID, "live", "2nd_half")));
        when(liveMapper.timelineBatches(delta.payload(), TimelineSourceType.LIVE_DELTA, delta.rawPayloadId()))
                .thenReturn(List.of());
        when(liveMapper.timelineBatches(liveTimeline.payload(), TimelineSourceType.LIVE_TIMELINE, liveTimeline.rawPayloadId()))
                .thenReturn(List.of());
        when(liveMapper.timelineBatches(richTimeline.payload(), TimelineSourceType.EXTENDED, richTimeline.rawPayloadId()))
                .thenReturn(List.of(richBatch));
        when(entityManager.getReference(eq(RawPayloadEntity.class), any(UUID.class))).thenReturn(rawPayload);
        when(eventIngestionService.upsertEvents(match, List.of(xgEvent))).thenReturn(new EventWriteCounts(1, 0));
        when(rebuildService.rebuild(MATCH_ID)).thenReturn(new RebuildMatchStateResult(MATCH_ID, 1, 1, 1, 1));
        when(alertGenerationService.generate(MATCH_ID)).thenReturn(1);

        LivePollResult result = service.pollOnce();

        assertThat(result.registeredMatches()).isEqualTo(1);
        assertThat(result.processedMatches()).isEqualTo(1);
        assertThat(result.eventsInserted()).isEqualTo(1);
        assertThat(result.alertsCreated()).isEqualTo(1);
        assertThat(tracking.getLastRichTimelineRefreshAt()).isEqualTo(NOW);
        assertThat(tracking.getTrackingStatus()).isEqualTo(com.example.sportsanalytics.domain.model.LiveTrackingStatus.TRACKING);
        verify(sportradarClient).fetch(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE, PROVIDER_MATCH_ID, true);
        verify(eventIngestionService).upsertEvents(match, List.of(xgEvent));
    }

    private MatchEntity match(CoverageMode coverageMode) {
        MatchEntity match = new MatchEntity();
        match.setId(MATCH_ID);
        match.setProviderMatchId(PROVIDER_MATCH_ID);
        match.setCoverageMode(coverageMode);
        return match;
    }

    private LiveMatchTrackingEntity tracking(MatchEntity match) {
        LiveMatchTrackingEntity tracking = new LiveMatchTrackingEntity();
        tracking.setMatch(match);
        tracking.setProviderMatchId(PROVIDER_MATCH_ID);
        tracking.setActive(true);
        tracking.setTrackingStatus(com.example.sportsanalytics.domain.model.LiveTrackingStatus.TRACKING);
        tracking.setStartedAt(NOW.minusSeconds(600));
        tracking.setCreatedAt(NOW.minusSeconds(600));
        tracking.setUpdatedAt(NOW.minusSeconds(600));
        return tracking;
    }

    private SportradarPayload payload(SportradarEndpoint endpoint, String providerId) {
        return new SportradarPayload(
                UUID.randomUUID(),
                endpoint,
                providerId,
                "/" + endpoint.sourceEndpoint() + ".json",
                200,
                null,
                NOW,
                null,
                "soccer-extended",
                false,
                objectMapper.createObjectNode()
        );
    }

    private NormalizedTimelineEvent xgEvent(UUID rawPayloadId) {
        return new NormalizedTimelineEvent(
                "event-xg-1",
                "shot_on_target",
                1,
                MatchEventType.SHOT,
                72,
                null,
                TeamSide.HOME,
                List.of("sr:player:1"),
                89,
                41,
                100,
                45,
                0.28,
                "saved",
                1,
                1,
                false,
                TimelineSourceType.EXTENDED,
                rawPayloadId
        );
    }
}
