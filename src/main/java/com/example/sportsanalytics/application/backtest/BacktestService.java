package com.example.sportsanalytics.application.backtest;

import com.example.sportsanalytics.analytics.backtest.BacktestMatchSample;
import com.example.sportsanalytics.analytics.backtest.BacktestMetrics;
import com.example.sportsanalytics.analytics.backtest.BacktestMetricsCalculator;
import com.example.sportsanalytics.analytics.backtest.BacktestProbabilitySample;
import com.example.sportsanalytics.analytics.backtest.Outcome;
import com.example.sportsanalytics.analytics.backtest.SeasonScheduleParser;
import com.example.sportsanalytics.analytics.probability.ExpectedGoalsProbabilityEngine;
import com.example.sportsanalytics.application.backtest.dto.BacktestFailureView;
import com.example.sportsanalytics.application.backtest.dto.BacktestRunView;
import com.example.sportsanalytics.application.backtest.dto.RunBacktestCommand;
import com.example.sportsanalytics.application.match.MatchTrackingUseCase;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.persistence.entity.BacktestRunEntity;
import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import com.example.sportsanalytics.persistence.repository.BacktestRunRepository;
import com.example.sportsanalytics.persistence.repository.FeatureSnapshotRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.persistence.repository.ProbabilitySnapshotRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacktestService {
    private final SportradarClient sportradarClient;
    private final MatchTrackingUseCase matchTrackingUseCase;
    private final MatchStateRepository matchStateRepository;
    private final FeatureSnapshotRepository featureSnapshotRepository;
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final ObjectMapper objectMapper;
    private final SeasonScheduleParser seasonScheduleParser = new SeasonScheduleParser();
    private final BacktestMetricsCalculator metricsCalculator = new BacktestMetricsCalculator();
    private final Clock clock;

    @Autowired
    public BacktestService(
            SportradarClient sportradarClient,
            MatchTrackingUseCase matchTrackingUseCase,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            BacktestRunRepository backtestRunRepository,
            ObjectMapper objectMapper
    ) {
        this(sportradarClient, matchTrackingUseCase, matchStateRepository, featureSnapshotRepository, probabilitySnapshotRepository,
                backtestRunRepository, objectMapper, Clock.systemUTC());
    }

    BacktestService(
            SportradarClient sportradarClient,
            MatchTrackingUseCase matchTrackingUseCase,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            BacktestRunRepository backtestRunRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.sportradarClient = sportradarClient;
        this.matchTrackingUseCase = matchTrackingUseCase;
        this.matchStateRepository = matchStateRepository;
        this.featureSnapshotRepository = featureSnapshotRepository;
        this.probabilitySnapshotRepository = probabilitySnapshotRepository;
        this.backtestRunRepository = backtestRunRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BacktestRunView run(RunBacktestCommand command) {
        Instant startedAt = clock.instant();
        List<String> sportEventIds = command.sportEventIds().isEmpty()
                ? seasonScheduleParser.finishedSportEventIds(
                        sportradarClient.fetch(SportradarEndpoint.SEASON_SCHEDULES, command.seasonId(), command.forceRefresh()).payload()
                )
                : command.sportEventIds();
        List<BacktestFailureView> failures = new ArrayList<>();
        List<BacktestMatchSample> samples = new ArrayList<>();

        for (String sportEventId : sportEventIds) {
            try {
                TrackMatchResult tracked = matchTrackingUseCase.track(new TrackMatchCommand(sportEventId, command.forceRefresh()));
                samples.add(sample(tracked));
            } catch (RuntimeException exception) {
                failures.add(new BacktestFailureView(sportEventId, exception.getMessage()));
                if (!command.continueOnMatchFailure()) {
                    break;
                }
            }
        }

        BacktestMetrics metrics = metricsCalculator.calculate(samples);
        BacktestRunEntity entity = new BacktestRunEntity();
        entity.setSeasonId(command.seasonId());
        entity.setModelVersion(ExpectedGoalsProbabilityEngine.MODEL_VERSION);
        entity.setStartedAt(startedAt);
        entity.setFinishedAt(clock.instant());
        entity.setStatus(status(samples.size(), failures.size(), sportEventIds.size()));
        entity.setRequestedMatchCount(sportEventIds.size());
        entity.setProcessedMatchCount(samples.size());
        entity.setFailedMatchCount(failures.size());
        entity.setMetricsJson(objectMapper.valueToTree(metrics));
        entity.setFailureJson(objectMapper.valueToTree(failures));
        return view(backtestRunRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public BacktestRunView get(UUID runId) {
        return backtestRunRepository.findById(runId)
                .map(this::view)
                .orElseThrow(() -> new BacktestRunNotFoundException(runId));
    }

    private BacktestMatchSample sample(TrackMatchResult tracked) {
        MatchStateEntity latestState = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(tracked.matchId())
                .orElseThrow(() -> new IllegalStateException("missing latest state for match " + tracked.matchId()));
        Map<String, MatchStateEntity> statesByEvent = matchStateRepository
                .findByMatchIdOrderByVersionAscWithEvent(tracked.matchId())
                .stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));
        Map<String, FeatureSnapshotEntity> featuresByEvent = featureSnapshotRepository
                .findByMatchIdOrderByTimeline(tracked.matchId())
                .stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));
        List<BacktestProbabilitySample> probabilities = probabilitySnapshotRepository
                .findByMatchIdOrderByTimeline(tracked.matchId())
                .stream()
                .map(snapshot -> probabilitySample(
                        snapshot,
                        statesByEvent.get(eventKey(snapshot.getEvent())),
                        featuresByEvent.get(eventKey(snapshot.getEvent()))
                ))
                .toList();
        return new BacktestMatchSample(
                tracked.matchId(),
                tracked.providerMatchId(),
                outcome(latestState.getHomeScore(), latestState.getAwayScore()),
                probabilities
        );
    }

    private Outcome outcome(int homeScore, int awayScore) {
        if (homeScore > awayScore) {
            return Outcome.HOME_WIN;
        }
        if (awayScore > homeScore) {
            return Outcome.AWAY_WIN;
        }
        return Outcome.DRAW;
    }

    private BacktestProbabilitySample probabilitySample(
            ProbabilitySnapshotEntity snapshot,
            MatchStateEntity state,
            FeatureSnapshotEntity feature
    ) {
        ProviderProbability providerProbability = providerProbability(feature);
        return new BacktestProbabilitySample(
                snapshot.getEvent() == null ? null : snapshot.getEvent().getEventSequence(),
                snapshot.getEvent() == null ? "UNKNOWN" : snapshot.getEvent().getEventType().name(),
                snapshot.getMinute(),
                state == null ? 0 : state.getHomeScore(),
                state == null ? 0 : state.getAwayScore(),
                snapshot.getHomeWin(),
                snapshot.getDraw(),
                snapshot.getAwayWin(),
                providerProbability == null ? null : providerProbability.homeWin(),
                providerProbability == null ? null : providerProbability.draw(),
                providerProbability == null ? null : providerProbability.awayWin()
        );
    }

    private ProviderProbability providerProbability(FeatureSnapshotEntity feature) {
        if (feature == null || feature.getFeaturesJson() == null) {
            return null;
        }
        JsonNode providerProbability = feature.getFeaturesJson().path("providerProbability");
        JsonNode home = providerProbability.path("homeWin");
        JsonNode draw = providerProbability.path("draw");
        JsonNode away = providerProbability.path("awayWin");
        if (!home.isNumber() || !draw.isNumber() || !away.isNumber()) {
            return null;
        }
        return new ProviderProbability(home.asDouble(), draw.asDouble(), away.asDouble());
    }

    private String eventKey(MatchStateEntity state) {
        return eventKey(state.getEvent());
    }

    private String eventKey(FeatureSnapshotEntity feature) {
        return eventKey(feature.getEvent());
    }

    private String eventKey(MatchEventEntity event) {
        return event == null ? "summary" : event.getId().toString();
    }

    private String status(int processed, int failed, int requested) {
        if (failed == 0) {
            return "COMPLETED";
        }
        if (processed == 0 || failed == requested) {
            return "FAILED";
        }
        return "COMPLETED_WITH_FAILURES";
    }

    private BacktestRunView view(BacktestRunEntity entity) {
        return new BacktestRunView(
                entity.getId(),
                entity.getSeasonId(),
                entity.getModelVersion(),
                entity.getStatus(),
                entity.getRequestedMatchCount(),
                entity.getProcessedMatchCount(),
                entity.getFailedMatchCount(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                map(entity.getMetricsJson()),
                failures(entity.getFailureJson())
        );
    }

    private Map<String, Object> map(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }

    private List<BacktestFailureView> failures(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<List<BacktestFailureView>>() {
        });
    }

    private record ProviderProbability(double homeWin, double draw, double awayWin) {
    }
}
