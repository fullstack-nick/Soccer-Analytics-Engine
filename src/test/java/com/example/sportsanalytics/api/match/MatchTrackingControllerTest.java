package com.example.sportsanalytics.api.match;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sportsanalytics.api.error.ApiExceptionHandler;
import com.example.sportsanalytics.application.match.MatchTrackingUseCase;
import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.StoredMatchView;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
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
    void returnsOrderedEvents() throws Exception {
        mockMvc.perform(get("/api/matches/{matchId}/events", MATCH_ID).param("type", "GOAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType").value("GOAL"));
    }

    @Test
    void resolvesProviderId() throws Exception {
        mockMvc.perform(get("/api/matches/provider").param("sportEventId", "sr:sport_event:70075140"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(MATCH_ID.toString()))
                .andExpect(jsonPath("$.providerMatchId").value("sr:sport_event:70075140"));
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
    }
}
