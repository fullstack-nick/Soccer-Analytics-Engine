package com.example.sportsanalytics.analytics.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.example.sportsanalytics.sportradar.mapping.TeamMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventSourcedMatchStateProjectorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventSourcedMatchStateProjector projector = new EventSourcedMatchStateProjector(objectMapper);

    @Test
    void rebuildsStateDeterministicallyFromStoredEvents() throws Exception {
        MatchEntity match = match();
        MatchMetadata metadata = metadata();
        List<MatchEventEntity> events = List.of(
                event(1, "match_started", MatchEventType.PERIOD, TeamSide.UNKNOWN, 0, 0, 0, false),
                event(2, "score_change", MatchEventType.GOAL, TeamSide.HOME, 30, 1, 0, true),
                event(3, "possible_goal", MatchEventType.SHOT, TeamSide.HOME, 44, null, null, false),
                event(4, "red_card", MatchEventType.RED_CARD, TeamSide.AWAY, 65, null, null, false),
                event(5, "substitution", MatchEventType.SUBSTITUTION, TeamSide.HOME, 70, null, null, false)
        );

        List<EventSourcedMatchState> snapshots = projector.project(
                match,
                metadata,
                CoverageMode.RICH,
                List.of("test"),
                events,
                fixture("lineups"),
                fixture("momentum"),
                fixture("season_info"),
                Map.of("summary", UUID.randomUUID())
        );

        EventSourcedMatchState latest = snapshots.get(snapshots.size() - 1);
        assertThat(snapshots).hasSize(events.size());
        assertThat(latest.homeScore()).isEqualTo(1);
        assertThat(latest.awayScore()).isEqualTo(0);
        assertThat(latest.awayRedCards()).isEqualTo(1);
        assertThat(latest.stateJson().path("cards").path("away").path("red").asInt()).isEqualTo(1);
        assertThat(latest.stateJson().path("substitutions").path("home").asInt()).isEqualTo(1);
        assertThat(latest.stateJson().path("accumulatedStats").path("home").path("confirmedGoals").asInt()).isEqualTo(1);
        assertThat(latest.stateJson().path("accumulatedStats").path("home").path("shots").asInt()).isEqualTo(1);
    }

    private MatchEntity match() {
        MatchEntity match = new MatchEntity();
        match.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        match.setProviderMatchId("sr:sport_event:test");
        match.setSeasonId("sr:season:test");
        match.setCompetitionId("sr:competition:test");
        match.setHomeTeamId("sr:competitor:home");
        match.setAwayTeamId("sr:competitor:away");
        match.setCoverageMode(CoverageMode.RICH);
        return match;
    }

    private MatchMetadata metadata() {
        return new MatchMetadata(
                "sr:sport_event:test",
                "sr:season:test",
                "sr:competition:test",
                new TeamMetadata("sr:competitor:home", "Home", TeamSide.HOME),
                new TeamMetadata("sr:competitor:away", "Away", TeamSide.AWAY),
                Instant.parse("2026-04-30T18:00:00Z"),
                0,
                0,
                "not_started",
                "not_started"
        );
    }

    private MatchEventEntity event(
            long sequence,
            String providerType,
            MatchEventType type,
            TeamSide side,
            int minute,
            Integer homeScoreAfter,
            Integer awayScoreAfter,
            boolean scoreChanged
    ) {
        MatchEventEntity event = new MatchEventEntity();
        event.setId(UUID.randomUUID());
        event.setProviderEventId("event-" + sequence);
        event.setProviderEventType(providerType);
        event.setEventSequence(sequence);
        event.setEventType(type);
        event.setTeamSide(side);
        event.setOccurredAtMinute(minute);
        event.setHomeScoreAfter(homeScoreAfter);
        event.setAwayScoreAfter(awayScoreAfter);
        event.setScoreChanged(scoreChanged);
        event.setOutcome(providerType);
        event.setSourceTimelineType(TimelineSourceType.EXTENDED);
        return event;
    }

    private com.fasterxml.jackson.databind.JsonNode fixture(String name) throws Exception {
        return objectMapper.readTree(getClass().getResourceAsStream("/fixtures/sportradar/" + name + ".json"));
    }
}
