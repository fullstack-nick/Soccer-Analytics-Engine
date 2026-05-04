package com.example.sportsanalytics.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sportsanalytics.application.backtest.dto.BacktestRunView;
import com.example.sportsanalytics.application.backtest.dto.RunBacktestCommand;
import com.example.sportsanalytics.application.match.MatchTrackingUseCase;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.BacktestRunEntity;
import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import com.example.sportsanalytics.persistence.repository.BacktestRunRepository;
import com.example.sportsanalytics.persistence.repository.FeatureSnapshotRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.persistence.repository.ProbabilitySnapshotRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BacktestServiceTest {
    private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant NOW = Instant.parse("2026-04-30T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SportradarClient sportradarClient = mock(SportradarClient.class);
    private final MatchTrackingUseCase matchTrackingUseCase = mock(MatchTrackingUseCase.class);
    private final MatchStateRepository matchStateRepository = mock(MatchStateRepository.class);
    private final FeatureSnapshotRepository featureSnapshotRepository = mock(FeatureSnapshotRepository.class);
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository = mock(ProbabilitySnapshotRepository.class);
    private final BacktestRunRepository backtestRunRepository = mock(BacktestRunRepository.class);
    private final BacktestService service = new BacktestService(
            sportradarClient,
            matchTrackingUseCase,
            matchStateRepository,
            featureSnapshotRepository,
            probabilitySnapshotRepository,
            backtestRunRepository,
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void explicitSportEventIdsDoNotFetchSeasonSchedule() {
        stubSuccessfulMatch("sr:sport_event:1");
        stubSave();

        BacktestRunView view = service.run(new RunBacktestCommand(
                "sr:season:1",
                List.of("sr:sport_event:1"),
                false,
                true
        ));

        assertThat(view.status()).isEqualTo("COMPLETED");
        assertThat(view.requestedMatchCount()).isEqualTo(1);
        assertThat(view.processedMatchCount()).isEqualTo(1);
        assertThat(view.metrics()).containsEntry("evaluationVersion", "stage5.5-v1");
        assertThat(view.metrics()).containsKeys("headline", "fixedMinuteMetrics", "baselines", "finalSnapshotDiagnostic");
        verify(sportradarClient, never()).fetch(any(), any(), anyBoolean());
    }

    @Test
    void fullSeasonBacktestUsesFinishedMatchesFromSeasonSchedule() throws Exception {
        when(sportradarClient.fetch(SportradarEndpoint.SEASON_SCHEDULES, "sr:season:1", false))
                .thenReturn(new SportradarPayload(
                        UUID.randomUUID(),
                        SportradarEndpoint.SEASON_SCHEDULES,
                        "sr:season:1",
                        "/season/schedules",
                        200,
                        "max-age=30",
                        NOW,
                        NOW.plusSeconds(30),
                        "soccer-extended",
                        false,
                        objectMapper.readTree("""
                                {
                                  "schedules": [
                                    {
                                      "sport_event": {"id": "sr:sport_event:1"},
                                      "sport_event_status": {"status": "closed", "match_status": "ended"}
                                    },
                                    {
                                      "sport_event": {"id": "sr:sport_event:2"},
                                      "sport_event_status": {"status": "not_started", "match_status": "not_started"}
                                    }
                                  ]
                                }
                                """)
                ));
        stubSuccessfulMatch("sr:sport_event:1");
        stubSave();

        BacktestRunView view = service.run(new RunBacktestCommand("sr:season:1", List.of(), false, true));

        assertThat(view.requestedMatchCount()).isEqualTo(1);
        assertThat(view.processedMatchCount()).isEqualTo(1);
        verify(matchTrackingUseCase).track(new TrackMatchCommand("sr:sport_event:1", false));
    }

    @Test
    void recordsIndividualMatchFailureWhenConfiguredToContinue() {
        when(matchTrackingUseCase.track(new TrackMatchCommand("sr:sport_event:bad", false)))
                .thenThrow(new IllegalStateException("provider failed"));
        stubSuccessfulMatch("sr:sport_event:1");
        stubSave();

        BacktestRunView view = service.run(new RunBacktestCommand(
                "sr:season:1",
                List.of("sr:sport_event:bad", "sr:sport_event:1"),
                false,
                true
        ));

        assertThat(view.status()).isEqualTo("COMPLETED_WITH_FAILURES");
        assertThat(view.processedMatchCount()).isEqualTo(1);
        assertThat(view.failedMatchCount()).isEqualTo(1);
        assertThat(view.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.sportEventId()).isEqualTo("sr:sport_event:bad"));
    }

    private void stubSuccessfulMatch(String sportEventId) {
        MatchEventEntity shot = event(1, MatchEventType.SHOT, 15, false);
        MatchEventEntity goal = event(2, MatchEventType.GOAL, 60, true);
        when(matchTrackingUseCase.track(new TrackMatchCommand(sportEventId, false))).thenReturn(trackResult(sportEventId));
        when(matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(MATCH_ID)).thenReturn(Optional.of(state()));
        when(matchStateRepository.findByMatchIdOrderByVersionAscWithEvent(MATCH_ID)).thenReturn(List.of(
                state(shot, 1, 15, 0, 0),
                state(goal, 2, 60, 1, 0)
        ));
        when(featureSnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(
                feature(shot, true),
                feature(goal, false)
        ));
        when(probabilitySnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(
                probability(shot, 15, 0.55, 0.25, 0.20),
                probability(goal, 60, 0.80, 0.15, 0.05)
        ));
    }

    private void stubSave() {
        ArgumentCaptor<BacktestRunEntity> captor = ArgumentCaptor.forClass(BacktestRunEntity.class);
        when(backtestRunRepository.save(captor.capture())).thenAnswer(invocation -> {
            BacktestRunEntity entity = invocation.getArgument(0);
            entity.setId(RUN_ID);
            return entity;
        });
    }

    private TrackMatchResult trackResult(String sportEventId) {
        return new TrackMatchResult(
                MATCH_ID,
                sportEventId,
                CoverageMode.RICH,
                2,
                2,
                2,
                2,
                new TeamView("home", "Home"),
                new TeamView("away", "Away"),
                90,
                2,
                1,
                2,
                0,
                List.of("summary"),
                List.of(),
                List.of()
        );
    }

    private MatchStateEntity state() {
        return state(null, 2, 90, 2, 1);
    }

    private MatchStateEntity state(MatchEventEntity event, long version, int minute, int homeScore, int awayScore) {
        MatchStateEntity state = new MatchStateEntity();
        state.setId(UUID.randomUUID());
        state.setMatch(match());
        state.setEvent(event);
        state.setVersion(version);
        state.setMinute(minute);
        state.setHomeScore(homeScore);
        state.setAwayScore(awayScore);
        state.setHomeRedCards(0);
        state.setAwayRedCards(0);
        state.setStateJson(objectMapper.createObjectNode());
        state.setUpdatedAt(NOW);
        return state;
    }

    private FeatureSnapshotEntity feature(MatchEventEntity event, boolean withProviderProbability) {
        FeatureSnapshotEntity feature = new FeatureSnapshotEntity();
        feature.setId(UUID.randomUUID());
        feature.setMatch(match());
        feature.setEvent(event);
        feature.setMinute(event.getOccurredAtMinute());
        var features = objectMapper.createObjectNode();
        if (withProviderProbability) {
            features.set("providerProbability", objectMapper.createObjectNode()
                    .put("homeWin", 0.50)
                    .put("draw", 0.30)
                    .put("awayWin", 0.20));
        }
        feature.setFeaturesJson(features);
        feature.setCoverageMode(CoverageMode.RICH);
        feature.setFeatureSetVersion("stage5.5-v1");
        feature.setAvailabilityJson(objectMapper.createObjectNode());
        feature.setCreatedAt(NOW);
        return feature;
    }

    private ProbabilitySnapshotEntity probability(MatchEventEntity event, int minute, double home, double draw, double away) {
        ProbabilitySnapshotEntity probability = new ProbabilitySnapshotEntity();
        probability.setId(UUID.randomUUID());
        probability.setMatch(match());
        probability.setEvent(event);
        probability.setMinute(minute);
        probability.setHomeWin(home);
        probability.setDraw(draw);
        probability.setAwayWin(away);
        probability.setModelVersion("xg-poisson-v1.3");
        probability.setModelConfidence(0.80);
        probability.setCoverageQuality("HIGH");
        probability.setExplanationsJson(objectMapper.createArrayNode());
        probability.setFeatureContributionsJson(objectMapper.createObjectNode());
        probability.setCreatedAt(NOW);
        return probability;
    }

    private MatchEventEntity event(long sequence, MatchEventType type, int minute, boolean scoreChanged) {
        MatchEventEntity event = new MatchEventEntity();
        event.setId(UUID.randomUUID());
        event.setMatch(match());
        event.setProviderEventId("event-" + sequence);
        event.setEventSequence(sequence);
        event.setEventType(type);
        event.setOccurredAtMinute(minute);
        event.setTeamSide(TeamSide.HOME);
        event.setPlayerIds(objectMapper.createArrayNode());
        event.setScoreChanged(scoreChanged);
        event.setSourceTimelineType(TimelineSourceType.EXTENDED);
        event.setReceivedAt(NOW);
        return event;
    }

    private MatchEntity match() {
        MatchEntity match = new MatchEntity();
        match.setId(MATCH_ID);
        match.setProviderMatchId("sr:sport_event:1");
        match.setSeasonId("sr:season:1");
        match.setCompetitionId("sr:competition:1");
        match.setHomeTeamId("home");
        match.setAwayTeamId("away");
        match.setCoverageMode(CoverageMode.RICH);
        return match;
    }
}

