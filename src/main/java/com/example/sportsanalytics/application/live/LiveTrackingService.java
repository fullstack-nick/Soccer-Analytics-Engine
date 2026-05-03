package com.example.sportsanalytics.application.live;

import com.example.sportsanalytics.application.live.dto.LiveTrackingView;
import com.example.sportsanalytics.application.match.MatchNotFoundException;
import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.domain.model.LiveTrackingStatus;
import com.example.sportsanalytics.persistence.entity.LiveMatchTrackingEntity;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import com.example.sportsanalytics.persistence.repository.LiveMatchTrackingRepository;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveTrackingService {
    private final MatchRepository matchRepository;
    private final LiveMatchTrackingRepository liveTrackingRepository;
    private final MatchStateRepository matchStateRepository;
    private final ProbabilitySnapshotRepository probabilitySnapshotRepository;
    private final MatchAlertRepository matchAlertRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public LiveTrackingService(
            MatchRepository matchRepository,
            LiveMatchTrackingRepository liveTrackingRepository,
            MatchStateRepository matchStateRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            MatchAlertRepository matchAlertRepository,
            ObjectMapper objectMapper
    ) {
        this(matchRepository, liveTrackingRepository, matchStateRepository, probabilitySnapshotRepository,
                matchAlertRepository, objectMapper, Clock.systemUTC());
    }

    LiveTrackingService(
            MatchRepository matchRepository,
            LiveMatchTrackingRepository liveTrackingRepository,
            MatchStateRepository matchStateRepository,
            ProbabilitySnapshotRepository probabilitySnapshotRepository,
            MatchAlertRepository matchAlertRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.liveTrackingRepository = liveTrackingRepository;
        this.matchStateRepository = matchStateRepository;
        this.probabilitySnapshotRepository = probabilitySnapshotRepository;
        this.matchAlertRepository = matchAlertRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public LiveTrackingView start(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        Instant now = clock.instant();
        LiveMatchTrackingEntity tracking = liveTrackingRepository.findByMatch_Id(matchId).orElseGet(() -> {
            LiveMatchTrackingEntity entity = new LiveMatchTrackingEntity();
            entity.setMatch(match);
            entity.setProviderMatchId(match.getProviderMatchId());
            entity.setCreatedAt(now);
            return entity;
        });
        tracking.setMatch(match);
        tracking.setProviderMatchId(match.getProviderMatchId());
        tracking.setTrackingStatus(LiveTrackingStatus.TRACKING);
        tracking.setActive(true);
        tracking.setStartedAt(tracking.getStartedAt() == null ? now : tracking.getStartedAt());
        tracking.setStoppedAt(null);
        tracking.setLastError(null);
        tracking.setUpdatedAt(now);
        return view(liveTrackingRepository.save(tracking));
    }

    @Transactional
    public LiveTrackingView stop(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        LiveMatchTrackingEntity tracking = liveTrackingRepository.findByMatch_Id(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        Instant now = clock.instant();
        tracking.setActive(false);
        tracking.setTrackingStatus(LiveTrackingStatus.STOPPED);
        tracking.setStoppedAt(now);
        tracking.setUpdatedAt(now);
        return view(liveTrackingRepository.save(tracking));
    }

    @Transactional(readOnly = true)
    public List<LiveTrackingView> trackedMatches() {
        return liveTrackingRepository.findByOrderByUpdatedAtDesc().stream()
                .map(this::view)
                .toList();
    }

    LiveTrackingView view(LiveMatchTrackingEntity tracking) {
        MatchEntity match = tracking.getMatch();
        MatchStateEntity state = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(match.getId()).orElse(null);
        ProbabilitySnapshotView probability = probabilitySnapshotRepository.findFirstLatestByMatchId(match.getId())
                .map(this::probabilityView)
                .orElse(null);
        return new LiveTrackingView(
                tracking.getId(),
                match.getId(),
                match.getProviderMatchId(),
                tracking.getTrackingStatus(),
                tracking.isActive(),
                match.getCoverageMode(),
                team(match.getHomeTeamId(), state == null ? null : state.getStateJson().path("teams").path("home").path("name").asText(null)),
                team(match.getAwayTeamId(), state == null ? null : state.getStateJson().path("teams").path("away").path("name").asText(null)),
                state == null ? null : state.getMinute(),
                state == null ? null : state.getHomeScore(),
                state == null ? null : state.getAwayScore(),
                probability,
                matchAlertRepository.countByMatch_Id(match.getId()),
                tracking.getStartedAt(),
                tracking.getStoppedAt(),
                tracking.getLastPollAt(),
                tracking.getLastSuccessAt(),
                tracking.getLastRichTimelineRefreshAt(),
                tracking.getLastErrorAt(),
                tracking.getErrorCount(),
                tracking.getLastError(),
                tracking.getUpdatedAt()
        );
    }

    private ProbabilitySnapshotView probabilityView(ProbabilitySnapshotEntity entity) {
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

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<List<String>>() {
        });
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

    private TeamView team(String id, String name) {
        return new TeamView(id, name);
    }
}
