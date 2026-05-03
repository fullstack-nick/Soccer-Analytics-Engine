package com.example.sportsanalytics.api.match;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sportsanalytics.api.error.ApiExceptionHandler;
import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.alert.dto.MatchAlertView;
import com.example.sportsanalytics.application.live.LiveTrackingService;
import com.example.sportsanalytics.application.live.dto.LiveTrackingView;
import com.example.sportsanalytics.application.match.MatchTrackingUseCase;
import com.example.sportsanalytics.application.match.dto.FeatureSnapshotView;
import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.ProbabilityTimelinePoint;
import com.example.sportsanalytics.application.match.dto.RebuildMatchStateResult;
import com.example.sportsanalytics.application.match.dto.RebuildProbabilityResult;
import com.example.sportsanalytics.application.match.dto.FinalScoreView;
import com.example.sportsanalytics.application.match.dto.ReplayMatchResult;
import com.example.sportsanalytics.application.match.dto.StoredMatchView;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.AlertSeverity;
import com.example.sportsanalytics.domain.model.AlertType;
import com.example.sportsanalytics.domain.model.LiveTrackingStatus;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonResult;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonTimelinePoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchTrackingController.class)
@Import({ApiExceptionHandler.class, MatchTrackingControllerTest.TestMatchTrackingConfiguration.class})
class MatchTrackingControllerTest {
    private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveTrackingService liveTrackingService;

    @Autowired
    private AlertGenerationService alertGenerationService;

    @Test
    void trackValidatesBlankSportEventId() throws Exception {
        mockMvc.perform(post("/api/matches/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportEventId\":\"\",\"forceRefresh\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void tracksMatch() throws Exception {
        mockMvc.perform(post("/api/matches/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportEventId\":\"sr:sport_event:70075140\",\"forceRefresh\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.providerMatchId").value("sr:sport_event:70075140"))
                .andExpect(jsonPath("$.coverageMode").value("RICH"))
                .andExpect(jsonPath("$.stateSnapshotsCreated").value(4))
                .andExpect(jsonPath("$.featureSnapshotsCreated").value(4))
                .andExpect(jsonPath("$.probabilitySnapshotsCreated").value(4))
                .andExpect(jsonPath("$.eventsInserted").value(4))
                .andExpect(jsonPath("$.rawPayloadsFetched", contains("summary", "extended_timeline")));
    }

    @Test
    void returnsLatestState() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/state", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.minute").value(65))
                .andExpect(jsonPath("$.state.coverageMode").value("RICH"));
    }

    @Test
    void rebuildsStateAndFeatures() throws Exception {
        mockMvc.perform(post("/api/matches/{matchId}/state/rebuild", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.stateSnapshotsCreated").value(4))
                .andExpect(jsonPath("$.featureSnapshotsCreated").value(4))
                .andExpect(jsonPath("$.probabilitySnapshotsCreated").value(4))
                .andExpect(jsonPath("$.latestStateVersion").value(4));
    }

    @Test
    void rebuildsProbabilities() throws Exception {
        mockMvc.perform(post("/api/matches/{matchId}/probabilities/rebuild", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.probabilitySnapshotsCreated").value(4));
    }

    @Test
    void replaysMatch() throws Exception {
        mockMvc.perform(post("/api/matches/{matchId}/replay", MATCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.eventCount").value(1))
                .andExpect(jsonPath("$.probabilitySnapshotsCreated").value(4))
                .andExpect(jsonPath("$.finalScore.home").value(1))
                .andExpect(jsonPath("$.latestProbability.homeWin").value(0.72));
    }

    @Test
    void returnsStateTimeline() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/states", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stateVersion").value(1));
    }

    @Test
    void returnsOrderedEvents() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/events", MATCH_ID).param("type", "GOAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType").value("GOAL"));
    }

    @Test
    void returnsFeatureSnapshots() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/features", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].features.scoreDifference").value(1))
                .andExpect(jsonPath("$[0].availability.availableFeatures[0]").value("scoreDifference"));
    }

    @Test
    void returnsLatestFeatureSnapshot() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/features/latest", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.scoreDifference").value(1));
    }

    @Test
    void returnsProbabilitySnapshots() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/probabilities", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].homeWin").value(0.72))
                .andExpect(jsonPath("$[0].modelVersion").value("xg-poisson-v1.1"))
                .andExpect(jsonPath("$[0].featureContributions.expectedHomeGoalsRemaining").value(0.4));
    }

    @Test
    void returnsLatestProbabilitySnapshot() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/probabilities/latest", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeWin").value(0.72))
                .andExpect(jsonPath("$.coverageQuality").value("HIGH"));
    }

    @Test
    void returnsProbabilityTimeline() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/probabilities/timeline", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].homeScore").value(1))
                .andExpect(jsonPath("$[0].awayScore").value(0))
                .andExpect(jsonPath("$[0].homeWin").value(0.72));
    }

    @Test
    void returnsModelComparison() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/model-comparison", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerAvailable").value(true))
                .andExpect(jsonPath("$.comparedSnapshotCount").value(1))
                .andExpect(jsonPath("$.maxDivergence").value(0.12));
    }

    @Test
    void resolvesProviderId() throws Exception {
        mockMvc.perform(get("/api/matches/provider").param("sportEventId", "sr:sport_event:70075140"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.providerMatchId").value("sr:sport_event:70075140"));
    }

    @Test
    void startsLiveTracking() throws Exception {
        when(liveTrackingService.start(MATCH_ID)).thenReturn(liveTrackingView(true, LiveTrackingStatus.TRACKING));

        mockMvc.perform(post("/api/matches/{matchId}/track", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.trackingStatus").value("TRACKING"));
    }

    @Test
    void stopsLiveTracking() throws Exception {
        when(liveTrackingService.stop(MATCH_ID)).thenReturn(liveTrackingView(false, LiveTrackingStatus.STOPPED));

        mockMvc.perform(delete("/api/matches/{matchId}/track", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.trackingStatus").value("STOPPED"));
    }

    @Test
    void listsLiveTrackedMatches() throws Exception {
        when(liveTrackingService.trackedMatches()).thenReturn(List.of(liveTrackingView(true, LiveTrackingStatus.TRACKING)));

        mockMvc.perform(get("/api/matches/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].matchId").value(MATCH_ID.toString()));
    }

    @Test
    void returnsAlerts() throws Exception {
        when(alertGenerationService.alerts(MATCH_ID)).thenReturn(List.of(new MatchAlertView(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                MATCH_ID,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                AlertType.MODEL_PROVIDER_DIVERGENCE,
                AlertSeverity.WARNING,
                75,
                "Model-provider divergence",
                "Model probability differs materially from available provider probability.",
                Map.of("divergence", 0.14),
                Instant.parse("2026-04-30T00:00:00Z")
        )));

        mockMvc.perform(get("/api/matches/{matchId}/alerts", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].alertType").value("MODEL_PROVIDER_DIVERGENCE"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"));
    }

    private LiveTrackingView liveTrackingView(boolean active, LiveTrackingStatus status) {
        return new LiveTrackingView(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                MATCH_ID,
                "sr:sport_event:70075140",
                status,
                active,
                CoverageMode.RICH,
                new TeamView("sr:competitor:3224", "Home Team"),
                new TeamView("sr:competitor:266595", "Away Team"),
                65,
                1,
                0,
                null,
                1,
                Instant.parse("2026-04-30T00:00:00Z"),
                active ? null : Instant.parse("2026-04-30T00:10:00Z"),
                Instant.parse("2026-04-30T00:05:00Z"),
                Instant.parse("2026-04-30T00:05:00Z"),
                Instant.parse("2026-04-30T00:04:00Z"),
                null,
                0,
                null,
                Instant.parse("2026-04-30T00:05:00Z")
        );
    }

    @TestConfiguration
    static class TestMatchTrackingConfiguration {
        @Bean
        MatchTrackingUseCase matchTrackingUseCase() {
            ObjectMapper objectMapper = new ObjectMapper();
            return new MatchTrackingUseCase() {
                @Override
                public TrackMatchResult track(TrackMatchCommand command) {
                    return new TrackMatchResult(
                            MATCH_ID,
                            command.sportEventId(),
                            CoverageMode.RICH,
                            1,
                            4,
                            4,
                            4,
                            new TeamView("sr:competitor:3224", "Home Team"),
                            new TeamView("sr:competitor:266595", "Away Team"),
                            65,
                            1,
                            0,
                            4,
                            0,
                            List.of("summary", "extended_timeline"),
                            List.of(),
                            List.of()
                    );
                }

                @Override
                public MatchStateView latestState(UUID matchId) {
                    return new MatchStateView(
                            MATCH_ID,
                            UUID.fromString("22222222-2222-2222-2222-222222222222"),
                            "sr:sport_event:70075140",
                            CoverageMode.RICH,
                            1,
                            new TeamView("sr:competitor:3224", "Home Team"),
                            new TeamView("sr:competitor:266595", "Away Team"),
                            65,
                            1,
                            0,
                            0,
                            1,
                            Map.of("coverageMode", "RICH"),
                            Instant.parse("2026-04-30T00:00:00Z")
                    );
                }

                @Override
                public List<MatchStateView> states(UUID matchId) {
                    return List.of(latestState(matchId));
                }

                @Override
                public List<MatchEventView> events(UUID matchId, MatchEventType type) {
                    return List.of(new MatchEventView(
                            UUID.fromString("22222222-2222-2222-2222-222222222222"),
                            MATCH_ID,
                            "12",
                            "score_change",
                            3,
                            MatchEventType.GOAL,
                            30,
                            null,
                            TeamSide.HOME,
                            List.of("sr:player:2"),
                            null,
                            null,
                            null,
                            null,
                            0.33,
                            "score_change",
                            1,
                            0,
                            true,
                            TimelineSourceType.EXTENDED,
                            Instant.parse("2026-04-30T00:00:00Z")
                    ));
                }

                @Override
                public RebuildMatchStateResult rebuildState(UUID matchId) {
                    return new RebuildMatchStateResult(MATCH_ID, 4, 4, 4, 4);
                }

                @Override
                public RebuildProbabilityResult rebuildProbabilities(UUID matchId) {
                    return new RebuildProbabilityResult(MATCH_ID, 4);
                }

                @Override
                public ReplayMatchResult replay(UUID matchId, boolean forceRefresh) {
                    return new ReplayMatchResult(
                            MATCH_ID,
                            "sr:sport_event:70075140",
                            CoverageMode.RICH,
                            1,
                            4,
                            4,
                            4,
                            new FinalScoreView(1, 0),
                            latestProbability(matchId)
                    );
                }

                @Override
                public List<FeatureSnapshotView> features(UUID matchId) {
                    return List.of(latestFeature(matchId));
                }

                @Override
                public FeatureSnapshotView latestFeature(UUID matchId) {
                    return new FeatureSnapshotView(
                            UUID.fromString("33333333-3333-3333-3333-333333333333"),
                            MATCH_ID,
                            UUID.fromString("22222222-2222-2222-2222-222222222222"),
                            3L,
                            30,
                            CoverageMode.RICH,
                            "stage5.5-v1",
                            Map.of("scoreDifference", 1),
                            Map.of(
                                    "availableFeatures", List.of("scoreDifference"),
                                    "missingFeatures", List.of("providerProbability")
                            ),
                            Instant.parse("2026-04-30T00:00:00Z")
                    );
                }

                @Override
                public List<ProbabilitySnapshotView> probabilities(UUID matchId) {
                    return List.of(latestProbability(matchId));
                }

                @Override
                public ProbabilitySnapshotView latestProbability(UUID matchId) {
                    return new ProbabilitySnapshotView(
                            UUID.fromString("44444444-4444-4444-4444-444444444444"),
                            MATCH_ID,
                            UUID.fromString("22222222-2222-2222-2222-222222222222"),
                            3L,
                            30,
                            0.72,
                            0.18,
                            0.10,
                            "xg-poisson-v1.1",
                            0.81,
                            "HIGH",
                            List.of("Score is 1-0 in minute 30."),
                            Map.of("expectedHomeGoalsRemaining", 0.4),
                            Instant.parse("2026-04-30T00:00:00Z")
                    );
                }

                @Override
                public List<ProbabilityTimelinePoint> probabilityTimeline(UUID matchId) {
                    return List.of(new ProbabilityTimelinePoint(
                            UUID.fromString("44444444-4444-4444-4444-444444444444"),
                            MATCH_ID,
                            UUID.fromString("22222222-2222-2222-2222-222222222222"),
                            3L,
                            30,
                            1,
                            0,
                            0.72,
                            0.18,
                            0.10,
                            "xg-poisson-v1.1",
                            0.81,
                            "HIGH",
                            List.of("Score is 1-0 in minute 30."),
                            Map.of("expectedHomeGoalsRemaining", 0.4),
                            Instant.parse("2026-04-30T00:00:00Z")
                    ));
                }

                @Override
                public ModelComparisonResult modelComparison(UUID matchId) {
                    return new ModelComparisonResult(
                            MATCH_ID,
                            "sr:sport_event:70075140",
                            true,
                            "Provider probability comparison available.",
                            1,
                            0.12,
                            0.12,
                            List.of(new ModelComparisonTimelinePoint(
                                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                    3L,
                                    30,
                                    0.72,
                                    0.18,
                                    0.10,
                                    0.60,
                                    0.25,
                                    0.15,
                                    0.12
                            ))
                    );
                }

                @Override
                public StoredMatchView findByProviderMatchId(String providerMatchId) {
                    return new StoredMatchView(
                            MATCH_ID,
                            providerMatchId,
                            "sr:season:138028",
                            "sr:competition:17",
                            new TeamView("sr:competitor:3224", "Home Team"),
                            new TeamView("sr:competitor:266595", "Away Team"),
                            Instant.parse("2026-04-30T18:00:00Z"),
                            CoverageMode.RICH
                    );
                }
            };
        }

        @Bean
        LiveTrackingService liveTrackingService() {
            return mock(LiveTrackingService.class);
        }

        @Bean
        AlertGenerationService alertGenerationService() {
            return mock(AlertGenerationService.class);
        }
    }
}
