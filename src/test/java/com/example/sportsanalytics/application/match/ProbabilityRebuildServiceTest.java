package com.example.sportsanalytics.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sportsanalytics.analytics.probability.ExpectedGoalsProbabilityEngine;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import com.example.sportsanalytics.persistence.repository.FeatureSnapshotRepository;
import com.example.sportsanalytics.persistence.repository.MatchAlertRepository;
import com.example.sportsanalytics.persistence.repository.MatchRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.persistence.repository.ProbabilitySnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ProbabilityRebuildServiceTest {
    private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-04-30T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchStateRepository matchStateRepository = mock(MatchStateRepository.class);
    private final FeatureSnapshotRepository featureSnapshotRepository = mock(FeatureSnapshotRepository.class);
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository = mock(ProbabilitySnapshotRepository.class);
    private final MatchAlertRepository matchAlertRepository = mock(MatchAlertRepository.class);
    private final ProbabilityRebuildService service = new ProbabilityRebuildService(
            matchRepository,
            matchStateRepository,
            featureSnapshotRepository,
            probabilitySnapshotRepository,
            matchAlertRepository,
            new ExpectedGoalsProbabilityEngine(),
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void rebuildCreatesOneProbabilityPerFeatureAndReplacesPreviousRows() {
        MatchEntity match = match();
        MatchEventEntity event = event(match);
        MatchStateEntity state = state(match, event);
        FeatureSnapshotEntity feature = feature(match, event);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(matchStateRepository.findByMatch_IdOrderByVersionAsc(MATCH_ID)).thenReturn(List.of(state));
        when(featureSnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(feature));
        when(probabilitySnapshotRepository.save(any(ProbabilitySnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.rebuild(MATCH_ID).probabilitySnapshotsCreated()).isEqualTo(1);
        assertThat(service.rebuild(MATCH_ID).probabilitySnapshotsCreated()).isEqualTo(1);

        InOrder inOrder = inOrder(matchAlertRepository, probabilitySnapshotRepository);
        inOrder.verify(matchAlertRepository).deleteAllByMatchId(MATCH_ID);
        inOrder.verify(probabilitySnapshotRepository).deleteByMatchId(MATCH_ID);
        inOrder.verify(probabilitySnapshotRepository).save(any(ProbabilitySnapshotEntity.class));
        inOrder.verify(matchAlertRepository).deleteAllByMatchId(MATCH_ID);
        inOrder.verify(probabilitySnapshotRepository).deleteByMatchId(MATCH_ID);
        inOrder.verify(probabilitySnapshotRepository).save(any(ProbabilitySnapshotEntity.class));
        verify(matchAlertRepository, times(2)).deleteAllByMatchId(MATCH_ID);
        verify(probabilitySnapshotRepository, times(2)).deleteByMatchId(MATCH_ID);
        verify(probabilitySnapshotRepository, times(2)).save(any(ProbabilitySnapshotEntity.class));
    }

    @Test
    void persistedProbabilityContainsModelMetadataAndReferencesEvent() {
        MatchEntity match = match();
        MatchEventEntity event = event(match);
        MatchStateEntity state = state(match, event);
        FeatureSnapshotEntity feature = feature(match, event);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(matchStateRepository.findByMatch_IdOrderByVersionAsc(MATCH_ID)).thenReturn(List.of(state));
        when(featureSnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(feature));
        ArgumentCaptor<ProbabilitySnapshotEntity> captor = ArgumentCaptor.forClass(ProbabilitySnapshotEntity.class);
        when(probabilitySnapshotRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.rebuild(MATCH_ID);

        ProbabilitySnapshotEntity saved = captor.getValue();
        assertThat(saved.getMatch()).isSameAs(match);
        assertThat(saved.getEvent()).isSameAs(event);
        assertThat(saved.getModelVersion()).isEqualTo(ExpectedGoalsProbabilityEngine.MODEL_VERSION);
        assertThat(saved.getCoverageQuality()).isEqualTo("HIGH");
        assertThat(saved.getModelConfidence()).isGreaterThan(0.75);
        assertThat(saved.getHomeWin() + saved.getDraw() + saved.getAwayWin()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(saved.getFeatureContributionsJson().path("expectedHomeGoalsRemaining").isNumber()).isTrue();
        assertThat(saved.getExplanationsJson()).isNotEmpty();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    private MatchEntity match() {
        MatchEntity match = new MatchEntity();
        match.setId(MATCH_ID);
        match.setProviderMatchId("sr:sport_event:70075140");
        match.setSeasonId("sr:season:1");
        match.setCompetitionId("sr:competition:1");
        match.setHomeTeamId("home");
        match.setAwayTeamId("away");
        match.setCoverageMode(CoverageMode.RICH);
        return match;
    }

    private MatchEventEntity event(MatchEntity match) {
        MatchEventEntity event = new MatchEventEntity();
        event.setId(EVENT_ID);
        event.setMatch(match);
        event.setProviderEventId("event-1");
        event.setEventSequence(1);
        event.setEventType(MatchEventType.SHOT);
        event.setOccurredAtMinute(70);
        event.setTeamSide(TeamSide.HOME);
        event.setPlayerIds(objectMapper.createArrayNode());
        event.setScoreChanged(false);
        event.setSourceTimelineType(TimelineSourceType.EXTENDED);
        event.setReceivedAt(NOW);
        return event;
    }

    private MatchStateEntity state(MatchEntity match, MatchEventEntity event) {
        MatchStateEntity state = new MatchStateEntity();
        state.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        state.setMatch(match);
        state.setEvent(event);
        state.setVersion(1);
        state.setMinute(70);
        state.setHomeScore(1);
        state.setAwayScore(1);
        state.setHomeRedCards(0);
        state.setAwayRedCards(1);
        ObjectNode stateJson = objectMapper.createObjectNode();
        stateJson.set("lineups", objectMapper.createObjectNode());
        ObjectNode momentum = objectMapper.createObjectNode();
        momentum.put("available", true);
        momentum.put("value", 25);
        stateJson.set("latestMomentum", momentum);
        stateJson.set("accumulatedStats", objectMapper.createObjectNode());
        state.setStateJson(stateJson);
        state.setUpdatedAt(NOW);
        return state;
    }

    private FeatureSnapshotEntity feature(MatchEntity match, MatchEventEntity event) {
        FeatureSnapshotEntity feature = new FeatureSnapshotEntity();
        feature.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        feature.setMatch(match);
        feature.setEvent(event);
        feature.setMinute(70);
        feature.setCoverageMode(CoverageMode.RICH);
        feature.setFeatureSetVersion("stage5.5-v1");
        ObjectNode features = objectMapper.createObjectNode();
        features.put("scoreDifference", 0);
        features.put("timeRemainingMinutes", 20);
        features.put("timeRemainingRatio", 20 / 90.0);
        features.put("homeAdvantage", 1.0);
        features.put("teamStrengthDelta", 0.2);
        features.put("redCardAdjustment", 0.15);
        features.put("xgDelta", 0.4);
        features.put("shotPressureDelta", 2.0);
        features.put("shotLocationQualityDelta", 0.2);
        features.put("fieldTilt", 0.5);
        features.put("possessionPressureDelta", 0.2);
        features.put("momentumTrend", 8.0);
        feature.setFeaturesJson(features);
        ObjectNode availability = objectMapper.createObjectNode();
        availability.put("coverageMode", "RICH");
        availability.put("featureSetVersion", "stage5.5-v1");
        availability.set("availableFeatures", objectMapper.valueToTree(List.of(
                "scoreDifference",
                "timeRemainingRatio",
                "homeAdvantage",
                "teamStrengthDelta",
                "redCardAdjustment",
                "xgDelta",
                "shotPressureDelta",
                "momentumTrend"
        )));
        availability.set("missingFeatures", objectMapper.valueToTree(List.of("providerProbability")));
        feature.setAvailabilityJson(availability);
        feature.setCreatedAt(NOW);
        return feature;
    }
}
