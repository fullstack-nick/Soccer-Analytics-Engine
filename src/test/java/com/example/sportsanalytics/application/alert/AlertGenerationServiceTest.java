package com.example.sportsanalytics.application.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.domain.model.AlertType;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import com.example.sportsanalytics.persistence.entity.MatchAlertEntity;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import com.example.sportsanalytics.persistence.repository.FeatureSnapshotRepository;
import com.example.sportsanalytics.persistence.repository.MatchAlertRepository;
import com.example.sportsanalytics.persistence.repository.MatchRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.persistence.repository.ProbabilitySnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlertGenerationServiceTest {
    private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchStateRepository matchStateRepository = mock(MatchStateRepository.class);
    private final FeatureSnapshotRepository featureSnapshotRepository = mock(FeatureSnapshotRepository.class);
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository = mock(ProbabilitySnapshotRepository.class);
    private final MatchAlertRepository matchAlertRepository = mock(MatchAlertRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlertGenerationService service = new AlertGenerationService(
            new SportsAnalyticsProperties(),
            matchRepository,
            matchStateRepository,
            featureSnapshotRepository,
            probabilitySnapshotRepository,
            matchAlertRepository,
            objectMapper,
            Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void createsProviderDivergenceAlertWhenThresholdIsExceeded() {
        MatchEntity match = match();
        ProbabilitySnapshotEntity probability = probability(0.70, 0.20, 0.10);
        FeatureSnapshotEntity feature = providerFeature(0.55, 0.30, 0.15);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(probabilitySnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(probability));
        when(featureSnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(feature));
        when(matchStateRepository.findByMatch_IdOrderByVersionAsc(MATCH_ID)).thenReturn(List.of());
        when(matchAlertRepository.existsByMatch_IdAndDeduplicationKey(eq(MATCH_ID), eq("MODEL_PROVIDER_DIVERGENCE:75")))
                .thenReturn(false);
        ArgumentCaptor<MatchAlertEntity> captor = ArgumentCaptor.forClass(MatchAlertEntity.class);

        int created = service.generate(MATCH_ID);

        assertThat(created).isEqualTo(1);
        verify(matchAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo(AlertType.MODEL_PROVIDER_DIVERGENCE);
        assertThat(captor.getValue().getMinute()).isEqualTo(75);
    }

    @Test
    void doesNotDuplicateExistingProviderDivergenceAlert() {
        MatchEntity match = match();
        ProbabilitySnapshotEntity probability = probability(0.70, 0.20, 0.10);
        FeatureSnapshotEntity feature = providerFeature(0.55, 0.30, 0.15);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(probabilitySnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(probability));
        when(featureSnapshotRepository.findByMatchIdOrderByTimeline(MATCH_ID)).thenReturn(List.of(feature));
        when(matchStateRepository.findByMatch_IdOrderByVersionAsc(MATCH_ID)).thenReturn(List.of());
        when(matchAlertRepository.existsByMatch_IdAndDeduplicationKey(eq(MATCH_ID), eq("MODEL_PROVIDER_DIVERGENCE:75")))
                .thenReturn(true);

        int created = service.generate(MATCH_ID);

        assertThat(created).isZero();
        verify(matchAlertRepository).existsByMatch_IdAndDeduplicationKey(MATCH_ID, "MODEL_PROVIDER_DIVERGENCE:75");
        verifyNoMoreInteractions(matchAlertRepository);
    }

    private MatchEntity match() {
        MatchEntity match = new MatchEntity();
        match.setId(MATCH_ID);
        match.setProviderMatchId("sr:sport_event:1");
        match.setSeasonId("sr:season:1");
        match.setCompetitionId("sr:competition:1");
        match.setHomeTeamId("sr:competitor:1");
        match.setAwayTeamId("sr:competitor:2");
        match.setCoverageMode(CoverageMode.RICH);
        return match;
    }

    private ProbabilitySnapshotEntity probability(double home, double draw, double away) {
        ProbabilitySnapshotEntity entity = new ProbabilitySnapshotEntity();
        entity.setMatch(match());
        entity.setMinute(75);
        entity.setHomeWin(home);
        entity.setDraw(draw);
        entity.setAwayWin(away);
        entity.setModelVersion("xg-poisson-v1.1");
        entity.setCoverageQuality("HIGH");
        entity.setExplanationsJson(objectMapper.createArrayNode());
        entity.setFeatureContributionsJson(objectMapper.createObjectNode());
        entity.setCreatedAt(Instant.parse("2026-04-30T00:00:00Z"));
        return entity;
    }

    private FeatureSnapshotEntity providerFeature(double home, double draw, double away) {
        FeatureSnapshotEntity entity = new FeatureSnapshotEntity();
        entity.setMatch(match());
        entity.setMinute(75);
        entity.setCoverageMode(CoverageMode.RICH);
        entity.setFeatureSetVersion("stage5.5-v1");
        entity.setFeaturesJson(objectMapper.createObjectNode()
                .set("providerProbability", objectMapper.createObjectNode()
                        .put("homeWin", home)
                        .put("draw", draw)
                        .put("awayWin", away)));
        entity.setAvailabilityJson(objectMapper.createObjectNode());
        entity.setCreatedAt(Instant.parse("2026-04-30T00:00:00Z"));
        return entity;
    }
}
