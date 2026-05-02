package com.example.sportsanalytics.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.repository.MatchEventRepository;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchEventIngestionServiceTest {
    private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-05-03T00:00:00Z");

    private final MatchEventRepository repository = mock(MatchEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MatchEventIngestionService service = new MatchEventIngestionService(
            repository,
            mock(EntityManager.class),
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void lowerDetailLiveTimelineUpdateDoesNotEraseExtendedXgAndCoordinates() {
        MatchEntity match = match();
        MatchEventEntity existing = event(match);
        existing.setProviderEventType("shot_on_target");
        existing.setEventType(MatchEventType.SHOT);
        existing.setTeamSide(TeamSide.HOME);
        existing.setPlayerIds(objectMapper.createArrayNode().add("sr:player:1"));
        existing.setX(89);
        existing.setY(42);
        existing.setDestinationX(100);
        existing.setDestinationY(47);
        existing.setXgValue(0.31);
        existing.setOutcome("saved");
        existing.setSourceTimelineType(TimelineSourceType.EXTENDED);
        when(repository.findByMatchIdAndProviderEventId(MATCH_ID, "event-1")).thenReturn(Optional.of(existing));

        service.upsertEvents(match, List.of(new NormalizedTimelineEvent(
                "event-1",
                "shot_on_target",
                18,
                MatchEventType.SHOT,
                34,
                null,
                TeamSide.UNKNOWN,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                TimelineSourceType.LIVE_TIMELINE,
                null
        )));

        assertThat(existing.getXgValue()).isEqualTo(0.31);
        assertThat(existing.getX()).isEqualTo(89);
        assertThat(existing.getY()).isEqualTo(42);
        assertThat(existing.getDestinationX()).isEqualTo(100);
        assertThat(existing.getDestinationY()).isEqualTo(47);
        assertThat(existing.getPlayerIds().size()).isEqualTo(1);
        assertThat(existing.getOutcome()).isEqualTo("saved");
        assertThat(existing.getTeamSide()).isEqualTo(TeamSide.HOME);
        assertThat(existing.getSourceTimelineType()).isEqualTo(TimelineSourceType.EXTENDED);
        assertThat(existing.getReceivedAt()).isEqualTo(NOW);
        verify(repository).save(existing);
    }

    @Test
    void extendedTimelineUpdateCanEnrichPreviouslyStoredLiveEvent() {
        MatchEntity match = match();
        MatchEventEntity existing = event(match);
        existing.setProviderEventType("shot");
        existing.setEventType(MatchEventType.SHOT);
        existing.setTeamSide(TeamSide.AWAY);
        existing.setPlayerIds(objectMapper.createArrayNode());
        existing.setSourceTimelineType(TimelineSourceType.LIVE_TIMELINE);
        when(repository.findByMatchIdAndProviderEventId(MATCH_ID, "event-1")).thenReturn(Optional.of(existing));

        service.upsertEvents(match, List.of(new NormalizedTimelineEvent(
                "event-1",
                "shot_on_target",
                18,
                MatchEventType.SHOT,
                34,
                2,
                TeamSide.HOME,
                List.of("sr:player:1"),
                89,
                42,
                100,
                47,
                0.31,
                "saved",
                null,
                null,
                false,
                TimelineSourceType.EXTENDED,
                null
        )));

        assertThat(existing.getProviderEventType()).isEqualTo("shot_on_target");
        assertThat(existing.getXgValue()).isEqualTo(0.31);
        assertThat(existing.getX()).isEqualTo(89);
        assertThat(existing.getDestinationX()).isEqualTo(100);
        assertThat(existing.getPlayerIds().size()).isEqualTo(1);
        assertThat(existing.getOutcome()).isEqualTo("saved");
        assertThat(existing.getTeamSide()).isEqualTo(TeamSide.HOME);
        assertThat(existing.getStoppageTime()).isEqualTo(2);
        assertThat(existing.getSourceTimelineType()).isEqualTo(TimelineSourceType.EXTENDED);
        verify(repository).save(existing);
    }

    @Test
    void scoreChangingGoalIsNotDowngradedByLaterSparseProviderUpdate() {
        MatchEntity match = match();
        MatchEventEntity existing = event(match);
        existing.setProviderEventType("score_change");
        existing.setEventType(MatchEventType.GOAL);
        existing.setTeamSide(TeamSide.HOME);
        existing.setPlayerIds(objectMapper.createArrayNode());
        existing.setHomeScoreAfter(1);
        existing.setAwayScoreAfter(0);
        existing.setScoreChanged(true);
        existing.setSourceTimelineType(TimelineSourceType.EXTENDED);
        when(repository.findByMatchIdAndProviderEventId(MATCH_ID, "event-1")).thenReturn(Optional.of(existing));

        service.upsertEvents(match, List.of(new NormalizedTimelineEvent(
                "event-1",
                "possible_goal",
                18,
                MatchEventType.SHOT,
                34,
                null,
                TeamSide.HOME,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                TimelineSourceType.LIVE_DELTA,
                null
        )));

        assertThat(existing.getEventType()).isEqualTo(MatchEventType.GOAL);
        assertThat(existing.isScoreChanged()).isTrue();
        assertThat(existing.getHomeScoreAfter()).isEqualTo(1);
        assertThat(existing.getAwayScoreAfter()).isEqualTo(0);
        assertThat(existing.getProviderEventType()).isEqualTo("score_change");
    }

    private MatchEntity match() {
        MatchEntity match = new MatchEntity();
        match.setId(MATCH_ID);
        return match;
    }

    private MatchEventEntity event(MatchEntity match) {
        MatchEventEntity event = new MatchEventEntity();
        event.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        event.setMatch(match);
        event.setProviderEventId("event-1");
        event.setEventSequence(17);
        event.setOccurredAtMinute(34);
        event.setStoppageTime(null);
        event.setScoreChanged(false);
        event.setReceivedAt(Instant.parse("2026-05-02T00:00:00Z"));
        return event;
    }
}
