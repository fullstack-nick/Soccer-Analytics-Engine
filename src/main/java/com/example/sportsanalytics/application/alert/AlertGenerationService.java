package com.example.sportsanalytics.application.alert;

import com.example.sportsanalytics.application.alert.dto.MatchAlertView;
import com.example.sportsanalytics.application.match.MatchNotFoundException;
import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.domain.model.AlertSeverity;
import com.example.sportsanalytics.domain.model.AlertType;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import com.example.sportsanalytics.persistence.entity.MatchAlertEntity;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertGenerationService {
    private final SportsAnalyticsProperties properties;
    private final MatchRepository matchRepository;
    private final MatchStateRepository matchStateRepository;
    private final FeatureSnapshotRepository featureSnapshotRepository;
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository;
    private final MatchAlertRepository matchAlertRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AlertGenerationService(
            SportsAnalyticsProperties properties,
            MatchRepository matchRepository,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            MatchAlertRepository matchAlertRepository,
            ObjectMapper objectMapper
    ) {
        this(properties, matchRepository, matchStateRepository, featureSnapshotRepository, probabilitySnapshotRepository,
                matchAlertRepository, objectMapper, Clock.systemUTC());
    }

    AlertGenerationService(
            SportsAnalyticsProperties properties,
            MatchRepository matchRepository,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            MatchAlertRepository matchAlertRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.matchRepository = matchRepository;
        this.matchStateRepository = matchStateRepository;
        this.featureSnapshotRepository = featureSnapshotRepository;
        this.probabilitySnapshotRepository = probabilitySnapshotRepository;
        this.matchAlertRepository = matchAlertRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public int generate(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        List<ProbabilitySnapshotEntity> probabilities = probabilitySnapshotRepository.findByMatchIdOrderByTimeline(matchId);
        if (probabilities.isEmpty()) {
            return 0;
        }
        Map<String, MatchStateEntity> statesByEvent = matchStateRepository.findByMatch_IdOrderByVersionAsc(matchId).stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));
        Map<String, FeatureSnapshotEntity> featuresByEvent = featureSnapshotRepository.findByMatchIdOrderByTimeline(matchId).stream()
                .collect(Collectors.toMap(this::eventKey, Function.identity(), (first, second) -> second, LinkedHashMap::new));

        int created = 0;
        created += providerDivergence(match, probabilities.get(probabilities.size() - 1),
                featuresByEvent.get(eventKey(probabilities.get(probabilities.size() - 1).getEvent())));
        created += redCardSwing(match, probabilities);
        created += pressureDespiteLosing(match, latestState(statesByEvent), latestFeature(featuresByEvent),
                probabilities.get(probabilities.size() - 1));
        created += xgContradictsScoreline(match, latestState(statesByEvent), latestFeature(featuresByEvent),
                probabilities.get(probabilities.size() - 1));
        created += lateMomentumShift(match, latestFeature(featuresByEvent), probabilities.get(probabilities.size() - 1));
        return created;
    }

    @Transactional(readOnly = true)
    public List<MatchAlertView> alerts(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        return matchAlertRepository.findByMatch_IdOrderByCreatedAtDesc(matchId).stream()
                .map(this::view)
                .toList();
    }

    private int providerDivergence(
            MatchEntity match,
            ProbabilitySnapshotEntity probability,
            FeatureSnapshotEntity feature
    ) {
        if (feature == null) {
            return 0;
        }
        JsonNode provider = feature.getFeaturesJson().path("providerProbability");
        if (!provider.isObject()) {
            return 0;
        }
        double divergence = maxDifference(
                probability.getHomeWin(),
                probability.getDraw(),
                probability.getAwayWin(),
                provider.path("homeWin").asDouble(Double.NaN),
                provider.path("draw").asDouble(Double.NaN),
                provider.path("awayWin").asDouble(Double.NaN)
        );
        double threshold = properties.getLive().getDivergenceAlertThreshold();
        if (!Double.isFinite(divergence) || divergence < threshold) {
            return 0;
        }
        AlertSeverity severity = divergence >= threshold * 1.5 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        return create(
                match,
                probability.getEvent(),
                probability,
                AlertType.MODEL_PROVIDER_DIVERGENCE,
                severity,
                probability.getMinute(),
                "Model-provider divergence",
                "Model probability differs materially from available provider probability.",
                "MODEL_PROVIDER_DIVERGENCE:" + minuteBucket(probability.getMinute()),
                details("divergence", divergence, "threshold", threshold)
        );
    }

    private int redCardSwing(MatchEntity match, List<ProbabilitySnapshotEntity> probabilities) {
        int created = 0;
        for (int index = 1; index < probabilities.size(); index++) {
            ProbabilitySnapshotEntity previous = probabilities.get(index - 1);
            ProbabilitySnapshotEntity current = probabilities.get(index);
            MatchEventEntity event = current.getEvent();
            if (!isRedCard(event)) {
                continue;
            }
            double movement = maxDifference(
                    current.getHomeWin(),
                    current.getDraw(),
                    current.getAwayWin(),
                    previous.getHomeWin(),
                    previous.getDraw(),
                    previous.getAwayWin()
            );
            double threshold = properties.getLive().getRedCardSwingThreshold();
            if (movement < threshold) {
                continue;
            }
            created += create(
                    match,
                    event,
                    current,
                    AlertType.RED_CARD_PROBABILITY_SWING,
                    movement >= threshold * 1.5 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                    current.getMinute(),
                    "Red-card probability swing",
                    "A red-card event caused a meaningful probability movement.",
                    "RED_CARD_PROBABILITY_SWING:" + eventKey(event),
                    details("movement", movement, "threshold", threshold)
            );
        }
        return created;
    }

    private int pressureDespiteLosing(
            MatchEntity match,
            MatchStateEntity state,
            FeatureSnapshotEntity feature,
            ProbabilitySnapshotEntity probability
    ) {
        if (state == null || feature == null) {
            return 0;
        }
        JsonNode features = feature.getFeaturesJson();
        int scoreDifference = state.getHomeScore() - state.getAwayScore();
        double pressure = nullableDouble(features, "shotPressureDelta").orElse(0.0);
        double xg = nullableDouble(features, "xgDelta").orElse(0.0);
        double fieldTilt = nullableDouble(features, "fieldTilt").orElse(0.0);
        double threshold = properties.getLive().getPressureAlertThreshold();
        TeamSide side = null;
        if (scoreDifference < 0 && (pressure >= threshold || xg >= 0.35 || fieldTilt >= 0.35)) {
            side = TeamSide.HOME;
        } else if (scoreDifference > 0 && (pressure <= -threshold || xg <= -0.35 || fieldTilt <= -0.35)) {
            side = TeamSide.AWAY;
        }
        if (side == null) {
            return 0;
        }
        return create(
                match,
                probability.getEvent(),
                probability,
                AlertType.PRESSURE_DESPITE_LOSING,
                AlertSeverity.INFO,
                feature.getMinute(),
                "Pressure despite losing",
                side + " is losing but recent pressure signals favor that side.",
                "PRESSURE_DESPITE_LOSING:" + minuteBucket(feature.getMinute()) + ":" + side,
                details("teamSide", side.name(), "shotPressureDelta", pressure, "xgDelta", xg, "fieldTilt", fieldTilt)
        );
    }

    private int xgContradictsScoreline(
            MatchEntity match,
            MatchStateEntity state,
            FeatureSnapshotEntity feature,
            ProbabilitySnapshotEntity probability
    ) {
        if (state == null || feature == null) {
            return 0;
        }
        int scoreDifference = state.getHomeScore() - state.getAwayScore();
        if (scoreDifference == 0) {
            return 0;
        }
        Optional<Double> xg = nullableDouble(feature.getFeaturesJson(), "xgDelta");
        double threshold = properties.getLive().getXgContradictionThreshold();
        if (xg.isEmpty() || Math.abs(xg.get()) < threshold || Math.signum(scoreDifference) == Math.signum(xg.get())) {
            return 0;
        }
        return create(
                match,
                probability.getEvent(),
                probability,
                AlertType.XG_CONTRADICTS_SCORELINE,
                AlertSeverity.WARNING,
                feature.getMinute(),
                "xG contradicts scoreline",
                "The scoreline favors one side while rolling xG favors the other.",
                "XG_CONTRADICTS_SCORELINE:" + minuteBucket(feature.getMinute()),
                details("scoreDifference", scoreDifference, "xgDelta", xg.get(), "threshold", threshold)
        );
    }

    private int lateMomentumShift(
            MatchEntity match,
            FeatureSnapshotEntity feature,
            ProbabilitySnapshotEntity probability
    ) {
        if (feature == null || feature.getMinute() < properties.getLive().getLateMomentumMinute()) {
            return 0;
        }
        Optional<Double> momentum = nullableDouble(feature.getFeaturesJson(), "momentumTrend");
        double threshold = properties.getLive().getLateMomentumThreshold();
        if (momentum.isEmpty() || Math.abs(momentum.get()) < threshold) {
            return 0;
        }
        return create(
                match,
                probability.getEvent(),
                probability,
                AlertType.LATE_MOMENTUM_SHIFT,
                AlertSeverity.INFO,
                feature.getMinute(),
                "Late momentum shift",
                "A large late-match momentum shift was detected.",
                "LATE_MOMENTUM_SHIFT:" + minuteBucket(feature.getMinute()),
                details("momentumTrend", momentum.get(), "threshold", threshold)
        );
    }

    private int create(
            MatchEntity match,
            MatchEventEntity event,
            ProbabilitySnapshotEntity probability,
            AlertType type,
            AlertSeverity severity,
            int minute,
            String title,
            String message,
            String deduplicationKey,
            JsonNode details
    ) {
        if (matchAlertRepository.existsByMatch_IdAndDeduplicationKey(match.getId(), deduplicationKey)) {
            return 0;
        }
        MatchAlertEntity entity = new MatchAlertEntity();
        entity.setMatch(match);
        entity.setEvent(event);
        entity.setProbabilitySnapshot(probability);
        entity.setAlertType(type);
        entity.setSeverity(severity);
        entity.setMinute(minute);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setDetailsJson(details);
        entity.setDeduplicationKey(deduplicationKey);
        entity.setCreatedAt(clock.instant());
        matchAlertRepository.save(entity);
        return 1;
    }

    private boolean isRedCard(MatchEventEntity event) {
        if (event == null || event.getEventType() == null) {
            return false;
        }
        if (event.getEventType().isRedCardEvent()) {
            return true;
        }
        if (!event.getEventType().isCardEvent()) {
            return false;
        }
        String type = text(event.getProviderEventType());
        String outcome = text(event.getOutcome());
        return type.contains("red") || outcome.contains("red") || type.contains("yellow_red") || outcome.contains("yellow_red");
    }

    private double maxDifference(
            double currentHome,
            double currentDraw,
            double currentAway,
            double previousHome,
            double previousDraw,
            double previousAway
    ) {
        return Math.max(
                Math.abs(currentHome - previousHome),
                Math.max(Math.abs(currentDraw - previousDraw), Math.abs(currentAway - previousAway))
        );
    }

    private Optional<Double> nullableDouble(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? Optional.of(value.asDouble()) : Optional.empty();
    }

    private JsonNode details(Object... values) {
        ObjectNode node = objectMapper.createObjectNode();
        for (int i = 0; i + 1 < values.length; i += 2) {
            String key = String.valueOf(values[i]);
            Object value = values[i + 1];
            if (value instanceof Number number) {
                node.put(key, number.doubleValue());
            } else if (value instanceof Boolean bool) {
                node.put(key, bool);
            } else {
                node.put(key, String.valueOf(value));
            }
        }
        return node;
    }

    private MatchStateEntity latestState(Map<String, MatchStateEntity> statesByEvent) {
        return statesByEvent.values().stream().reduce((first, second) -> second).orElse(null);
    }

    private FeatureSnapshotEntity latestFeature(Map<String, FeatureSnapshotEntity> featuresByEvent) {
        return featuresByEvent.values().stream().reduce((first, second) -> second).orElse(null);
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

    private int minuteBucket(int minute) {
        return (minute / 5) * 5;
    }

    private String text(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private MatchAlertView view(MatchAlertEntity entity) {
        return new MatchAlertView(
                entity.getId(),
                entity.getMatch().getId(),
                entity.getEvent() == null ? null : entity.getEvent().getId(),
                entity.getProbabilitySnapshot() == null ? null : entity.getProbabilitySnapshot().getId(),
                entity.getAlertType(),
                entity.getSeverity(),
                entity.getMinute(),
                entity.getTitle(),
                entity.getMessage(),
                map(entity.getDetailsJson()),
                entity.getCreatedAt()
        );
    }

    private Map<String, Object> map(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }
}
