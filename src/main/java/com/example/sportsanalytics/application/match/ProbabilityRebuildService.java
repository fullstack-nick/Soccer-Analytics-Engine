package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.ProbabilityTimelinePoint;
import com.example.sportsanalytics.application.match.dto.RebuildProbabilityResult;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonCalculator;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonPoint;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonResult;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.FeatureSnapshot;
import com.example.sportsanalytics.domain.model.MatchState;
import com.example.sportsanalytics.domain.model.Probability;
import com.example.sportsanalytics.domain.model.ProbabilitySnapshot;
import com.example.sportsanalytics.domain.probability.ProbabilityEngine;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProbabilityRebuildService {
    private final MatchRepository matchRepository;
    private final MatchStateRepository matchStateRepository;
    private final FeatureSnapshotRepository featureSnapshotRepository;
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository;
    private final MatchAlertRepository matchAlertRepository;
    private final ProbabilityEngine probabilityEngine;
    private final ModelComparisonCalculator modelComparisonCalculator = new ModelComparisonCalculator();
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ProbabilityRebuildService(
            MatchRepository matchRepository,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            MatchAlertRepository matchAlertRepository,
            ProbabilityEngine probabilityEngine,
            ObjectMapper objectMapper
    ) {
        this(matchRepository, matchStateRepository, featureSnapshotRepository, probabilitySnapshotRepository,
                matchAlertRepository, probabilityEngine, objectMapper, Clock.systemUTC());
    }

    ProbabilityRebuildService(
            MatchRepository matchRepository,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            MatchAlertRepository matchAlertRepository,
            ProbabilityEngine probabilityEngine,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchStateRepository = matchStateRepository;
        this.featureSnapshotRepository = featureSnapshotRepository;
        this.probabilitySnapshotRepository = probabilitySnapshotRepository;
        this.matchAlertRepository = matchAlertRepository;
        this.probabilityEngine = probabilityEngine;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public RebuildProbabilityResult rebuild(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        List<MatchStateEntity> states = matchStateRepository.findByMatch_IdOrderByVersionAsc(matchId);
        List<FeatureSnapshotEntity> features = featureSnapshotRepository.findByMatchIdOrderByTimeline(matchId);
        Map<String, MatchStateEntity> statesByEvent = states.stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));

        matchAlertRepository.deleteAllByMatchId(matchId);
        probabilitySnapshotRepository.deleteByMatchId(matchId);
        Instant now = clock.instant();
        int created = 0;
        for (FeatureSnapshotEntity featureEntity : features) {
            MatchStateEntity stateEntity = Optional.ofNullable(statesByEvent.get(eventKey(featureEntity.getEvent())))
                    .orElseThrow(() -> new IllegalStateException(
                            "missing state snapshot for feature event " + eventKey(featureEntity.getEvent())));
            ProbabilitySnapshot snapshot = probabilityEngine.calculate(
                    domainState(match, stateEntity),
                    domainFeature(match, featureEntity)
            );
            persist(match, featureEntity.getEvent(), featureEntity.getMinute(), snapshot, now);
            created++;
        }
        return new RebuildProbabilityResult(matchId, created);
    }

    @Transactional(readOnly = true)
    public List<ProbabilitySnapshotView> probabilities(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        return probabilitySnapshotRepository.findByMatchIdOrderByTimeline(matchId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProbabilitySnapshotView latestProbability(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        return probabilitySnapshotRepository.findFirstLatestByMatchId(matchId)
                .map(this::view)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
    }

    @Transactional(readOnly = true)
    public List<ProbabilityTimelinePoint> probabilityTimeline(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        Map<String, MatchStateEntity> statesByEvent = matchStateRepository.findByMatch_IdOrderByVersionAsc(matchId).stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));
        return probabilitySnapshotRepository.findByMatchIdOrderByTimeline(matchId).stream()
                .map(probability -> timelinePoint(probability, statesByEvent.get(eventKey(probability.getEvent()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public ModelComparisonResult modelComparison(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        Map<String, FeatureSnapshotEntity> featuresByEvent = featureSnapshotRepository.findByMatchIdOrderByTimeline(matchId).stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));
        List<ModelComparisonPoint> points = probabilitySnapshotRepository.findByMatchIdOrderByTimeline(matchId).stream()
                .map(probability -> comparisonPoint(probability, featuresByEvent.get(eventKey(probability.getEvent()))))
                .toList();
        return modelComparisonCalculator.compare(matchId, match.getProviderMatchId(), points);
    }

    private void persist(
            MatchEntity match,
            MatchEventEntity event,
            int minute,
            ProbabilitySnapshot snapshot,
            Instant now
    ) {
        ProbabilitySnapshotEntity entity = new ProbabilitySnapshotEntity();
        entity.setMatch(match);
        entity.setEvent(event);
        entity.setMinute(minute);
        entity.setHomeWin(snapshot.probability().homeWin());
        entity.setDraw(snapshot.probability().draw());
        entity.setAwayWin(snapshot.probability().awayWin());
        entity.setModelVersion(snapshot.modelVersion());
        entity.setModelConfidence(snapshot.modelConfidence());
        entity.setCoverageQuality(snapshot.coverageQuality());
        entity.setExplanationsJson(objectMapper.valueToTree(snapshot.explanations()));
        entity.setFeatureContributionsJson(objectMapper.valueToTree(snapshot.featureContributions()));
        entity.setCreatedAt(now);
        probabilitySnapshotRepository.save(entity);
    }

    private MatchState domainState(MatchEntity match, MatchStateEntity stateEntity) {
        JsonNode stateJson = stateEntity.getStateJson();
        return new MatchState(
                match.getId().toString(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getCoverageMode(),
                stateEntity.getMinute(),
                stateEntity.getHomeScore(),
                stateEntity.getAwayScore(),
                stateEntity.getHomeRedCards(),
                stateEntity.getAwayRedCards(),
                map(stateJson.path("lineups")),
                stateJson.path("latestMomentum").path("available").asBoolean(false)
                        ? stateJson.path("latestMomentum").path("value").asInt()
                        : null,
                map(stateJson.path("accumulatedStats")),
                stateEntity.getUpdatedAt()
        );
    }

    private FeatureSnapshot domainFeature(MatchEntity match, FeatureSnapshotEntity featureEntity) {
        JsonNode features = featureEntity.getFeaturesJson();
        JsonNode availability = featureEntity.getAvailabilityJson();
        return new FeatureSnapshot(
                match.getId().toString(),
                featureEntity.getMinute(),
                features.path("scoreDifference").asInt(0),
                features.path("timeRemainingMinutes").asInt(Math.max(0, 90 - featureEntity.getMinute())),
                features.path("timeRemainingRatio").asDouble(Math.max(0, 90 - featureEntity.getMinute()) / 90.0),
                features.path("homeAdvantage").asDouble(1.0),
                nullableDouble(features, "teamStrengthDelta"),
                nullableDouble(features, "lineupAdjustment"),
                nullableDouble(features, "redCardAdjustment"),
                nullableDouble(features, "xgDelta"),
                nullableDouble(features, "shotPressureDelta"),
                nullableDouble(features, "shotLocationQualityDelta"),
                nullableDouble(features, "fieldTilt"),
                nullableDouble(features, "possessionPressureDelta"),
                nullableDouble(features, "momentumTrend"),
                providerProbability(features.path("providerProbability")),
                coverageMode(featureEntity, availability),
                stringList(availability.path("availableFeatures")),
                stringList(availability.path("missingFeatures")),
                featureEntity.getCreatedAt()
        );
    }

    private CoverageMode coverageMode(FeatureSnapshotEntity featureEntity, JsonNode availability) {
        String value = availability.path("coverageMode").asText(featureEntity.getCoverageMode().name());
        try {
            return CoverageMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return featureEntity.getCoverageMode();
        }
    }

    private Probability providerProbability(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode home = node.path("homeWin");
        JsonNode draw = node.path("draw");
        JsonNode away = node.path("awayWin");
        if (!home.isNumber() || !draw.isNumber() || !away.isNumber()) {
            return null;
        }
        try {
            return new Probability(home.asDouble(), draw.asDouble(), away.asDouble());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Double nullableDouble(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.asDouble() : null;
    }

    private Map<String, Object> map(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<List<String>>() {
        });
    }

    private ProbabilitySnapshotView view(ProbabilitySnapshotEntity entity) {
        MatchEventEntity event = entity.getEvent();
        return new ProbabilitySnapshotView(
                entity.getId(),
                entity.getMatch().getId(),
                event == null ? null : event.getId(),
                event == null ? null : event.getEventSequence(),
                entity.getMinute(),
                entity.getHomeWin(),
                entity.getDraw(),
                entity.getAwayWin(),
                entity.getModelVersion(),
                entity.getModelConfidence(),
                entity.getCoverageQuality(),
                stringList(entity.getExplanationsJson()),
                doubleMap(entity.getFeatureContributionsJson()),
                entity.getCreatedAt()
        );
    }

    private ProbabilityTimelinePoint timelinePoint(ProbabilitySnapshotEntity entity, MatchStateEntity state) {
        MatchEventEntity event = entity.getEvent();
        return new ProbabilityTimelinePoint(
                entity.getId(),
                entity.getMatch().getId(),
                event == null ? null : event.getId(),
                event == null ? null : event.getEventSequence(),
                entity.getMinute(),
                state == null ? 0 : state.getHomeScore(),
                state == null ? 0 : state.getAwayScore(),
                entity.getHomeWin(),
                entity.getDraw(),
                entity.getAwayWin(),
                entity.getModelVersion(),
                entity.getModelConfidence(),
                entity.getCoverageQuality(),
                stringList(entity.getExplanationsJson()),
                doubleMap(entity.getFeatureContributionsJson()),
                entity.getCreatedAt()
        );
    }

    private ModelComparisonPoint comparisonPoint(ProbabilitySnapshotEntity entity, FeatureSnapshotEntity feature) {
        MatchEventEntity event = entity.getEvent();
        Probability provider = feature == null ? null : providerProbability(feature.getFeaturesJson().path("providerProbability"));
        return new ModelComparisonPoint(
                event == null ? null : event.getId(),
                event == null ? null : event.getEventSequence(),
                entity.getMinute(),
                entity.getHomeWin(),
                entity.getDraw(),
                entity.getAwayWin(),
                provider == null ? null : provider.homeWin(),
                provider == null ? null : provider.draw(),
                provider == null ? null : provider.awayWin()
        );
    }

    private Map<String, Double> doubleMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(field -> {
            if (field.getValue().isNumber()) {
                values.put(field.getKey(), field.getValue().asDouble());
            }
        });
        return values;
    }

    private String eventKey(FeatureSnapshotEntity feature) {
        return eventKey(feature.getEvent());
    }

    private String eventKey(MatchStateEntity state) {
        return eventKey(state.getEvent());
    }

    private String eventKey(MatchEventEntity event) {
        return event == null ? "summary" : event.getId().toString();
    }
}
