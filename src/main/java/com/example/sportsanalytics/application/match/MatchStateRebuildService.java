package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.analytics.features.FeatureExtractionResult;
import com.example.sportsanalytics.analytics.features.FeatureSourceContext;
import com.example.sportsanalytics.analytics.features.MatchFeatureExtractor;
import com.example.sportsanalytics.analytics.features.ProviderFeatureContext;
import com.example.sportsanalytics.analytics.features.ProviderFeatureContextExtractor;
import com.example.sportsanalytics.analytics.state.EventSourcedMatchState;
import com.example.sportsanalytics.analytics.state.EventSourcedMatchStateProjector;
import com.example.sportsanalytics.application.match.dto.FeatureSnapshotView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.RebuildMatchStateResult;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.FeatureSnapshotRepository;
import com.example.sportsanalytics.persistence.repository.MatchEventRepository;
import com.example.sportsanalytics.persistence.repository.MatchRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.persistence.repository.RawPayloadRepository;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.mapping.CoverageDetectionResult;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadataMapper;
import com.example.sportsanalytics.sportradar.mapping.TeamMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchStateRebuildService {
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchStateRepository matchStateRepository;
    private final FeatureSnapshotRepository featureSnapshotRepository;
    private final RawPayloadRepository rawPayloadRepository;
    private final MatchMetadataMapper metadataMapper;
    private final EventSourcedMatchStateProjector stateProjector;
    private final ProviderFeatureContextExtractor providerFeatureContextExtractor;
    private final MatchFeatureExtractor featureExtractor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MatchStateRebuildService(
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            RawPayloadRepository rawPayloadRepository,
            MatchMetadataMapper metadataMapper,
            EventSourcedMatchStateProjector stateProjector,
            ProviderFeatureContextExtractor providerFeatureContextExtractor,
            MatchFeatureExtractor featureExtractor,
            ObjectMapper objectMapper
    ) {
        this(matchRepository, matchEventRepository, matchStateRepository, featureSnapshotRepository, rawPayloadRepository,
                metadataMapper, stateProjector, providerFeatureContextExtractor, featureExtractor, objectMapper,
                Clock.systemUTC());
    }

    MatchStateRebuildService(
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchStateRepository matchStateRepository,
            FeatureSnapshotRepository featureSnapshotRepository,
            RawPayloadRepository rawPayloadRepository,
            MatchMetadataMapper metadataMapper,
            EventSourcedMatchStateProjector stateProjector,
            ProviderFeatureContextExtractor providerFeatureContextExtractor,
            MatchFeatureExtractor featureExtractor,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchStateRepository = matchStateRepository;
        this.featureSnapshotRepository = featureSnapshotRepository;
        this.rawPayloadRepository = rawPayloadRepository;
        this.metadataMapper = metadataMapper;
        this.stateProjector = stateProjector;
        this.providerFeatureContextExtractor = providerFeatureContextExtractor;
        this.featureExtractor = featureExtractor;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public RebuildMatchStateResult rebuild(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        StoredPayloadContext payloads = loadPayloadContext(match);
        MatchMetadata metadata = metadata(payloads.summary(), match);
        CoverageDetectionResult coverage = new CoverageDetectionResult(
                match.getCoverageMode(),
                coverageReasonsFromLatestState(matchId)
        );
        return rebuild(match, metadata, coverage, payloads);
    }

    @Transactional
    public RebuildMatchStateResult rebuild(
            MatchEntity match,
            MatchMetadata metadata,
            CoverageDetectionResult coverage,
            StoredPayloadContext payloads
    ) {
        List<MatchEventEntity> events = matchEventRepository.findByMatchIdOrderByEventSequenceAsc(match.getId());

        featureSnapshotRepository.deleteByMatchId(match.getId());
        matchStateRepository.deleteByMatchId(match.getId());

        List<EventSourcedMatchState> stateSnapshots = stateProjector.project(
                match,
                metadata,
                coverage.mode(),
                coverage.reasons(),
                events,
                payloads.lineups(),
                payloads.momentum(),
                payloads.seasonInfo(),
                payloads.payloadIds()
        );

        ProviderFeatureContext providerContext = providerFeatureContextExtractor.extract(
                metadata,
                payloads.standings(),
                payloads.formStandings(),
                payloads.seasonProbabilities()
        );
        FeatureSourceContext featureContext = new FeatureSourceContext(
                coverage.mode(),
                payloads.lineups(),
                payloads.momentum(),
                payloads.standings(),
                payloads.formStandings(),
                payloads.seasonProbabilities(),
                providerContext
        );

        Instant now = clock.instant();
        int featureCount = 0;
        long latestVersion = 0;
        for (EventSourcedMatchState snapshot : stateSnapshots) {
            MatchStateEntity stateEntity = persistState(match, snapshot, now);
            latestVersion = stateEntity.getVersion();
            persistFeature(match, snapshot, featureContext, now);
            featureCount++;
        }

        return new RebuildMatchStateResult(match.getId(), stateSnapshots.size(), featureCount, latestVersion);
    }

    @Transactional(readOnly = true)
    public List<MatchStateView> states(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        return matchStateRepository.findByMatch_IdOrderByVersionAsc(matchId).stream()
                .map(state -> stateView(match, state))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureSnapshotView> features(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        return featureSnapshotRepository.findByMatchIdOrderByTimeline(matchId).stream()
                .map(this::featureView)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeatureSnapshotView latestFeature(UUID matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        return featureSnapshotRepository.findFirstLatestByMatchId(matchId)
                .map(this::featureView)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
    }

    public StoredPayloadContext payloadContextFromTracking(
            JsonNode summary,
            JsonNode lineups,
            JsonNode momentum,
            JsonNode extendedSummary,
            JsonNode seasonInfo,
            JsonNode standings,
            JsonNode formStandings,
            JsonNode seasonProbabilities,
            Map<String, UUID> payloadIds
    ) {
        return new StoredPayloadContext(summary, lineups, momentum, extendedSummary, seasonInfo, standings,
                formStandings, seasonProbabilities, Map.copyOf(payloadIds));
    }

    private MatchStateEntity persistState(MatchEntity match, EventSourcedMatchState snapshot, Instant now) {
        MatchStateEntity entity = new MatchStateEntity();
        entity.setMatch(match);
        entity.setEvent(snapshot.event());
        entity.setVersion(snapshot.version());
        entity.setMinute(snapshot.minute());
        entity.setHomeScore(snapshot.homeScore());
        entity.setAwayScore(snapshot.awayScore());
        entity.setHomeRedCards(snapshot.homeRedCards());
        entity.setAwayRedCards(snapshot.awayRedCards());
        entity.setStateJson(snapshot.stateJson());
        entity.setUpdatedAt(now);
        return matchStateRepository.save(entity);
    }

    private void persistFeature(
            MatchEntity match,
            EventSourcedMatchState snapshot,
            FeatureSourceContext featureContext,
            Instant now
    ) {
        FeatureExtractionResult extracted = featureExtractor.extract(match, snapshot, featureContext);
        FeatureSnapshotEntity entity = new FeatureSnapshotEntity();
        entity.setMatch(match);
        entity.setEvent(snapshot.event());
        entity.setMinute(snapshot.minute());
        entity.setFeaturesJson(extracted.featuresJson());
        entity.setCoverageMode(extracted.coverageMode());
        entity.setFeatureSetVersion(extracted.featureSetVersion());
        entity.setAvailabilityJson(extracted.availabilityJson());
        entity.setCreatedAt(now);
        featureSnapshotRepository.save(entity);
    }

    private StoredPayloadContext loadPayloadContext(MatchEntity match) {
        Map<String, UUID> payloadIds = new LinkedHashMap<>();
        JsonNode summary = payload(SportradarEndpoint.SPORT_EVENT_SUMMARY, match.getProviderMatchId(), payloadIds);
        JsonNode lineups = payload(SportradarEndpoint.SPORT_EVENT_LINEUPS, match.getProviderMatchId(), payloadIds);
        JsonNode momentum = payload(SportradarEndpoint.SPORT_EVENT_MOMENTUM, match.getProviderMatchId(), payloadIds);
        JsonNode extendedSummary = payload(SportradarEndpoint.SPORT_EVENT_EXTENDED_SUMMARY, match.getProviderMatchId(), payloadIds);
        JsonNode seasonInfo = payload(SportradarEndpoint.SEASON_INFO, match.getSeasonId(), payloadIds);
        JsonNode standings = payload(SportradarEndpoint.SEASON_STANDINGS, match.getSeasonId(), payloadIds);
        JsonNode formStandings = payload(SportradarEndpoint.SEASON_FORM_STANDINGS, match.getSeasonId(), payloadIds);
        JsonNode seasonProbabilities = payload(SportradarEndpoint.SEASON_PROBABILITIES, match.getSeasonId(), payloadIds);
        return new StoredPayloadContext(summary, lineups, momentum, extendedSummary, seasonInfo, standings,
                formStandings, seasonProbabilities, payloadIds);
    }

    private JsonNode payload(SportradarEndpoint endpoint, String providerId, Map<String, UUID> payloadIds) {
        return rawPayloadRepository.findFirstByProviderIdAndSourceEndpointOrderByFetchedAtDesc(
                        providerId,
                        endpoint.sourceEndpoint()
                )
                .map(payload -> {
                    payloadIds.put(endpoint.sourceEndpoint(), payload.getId());
                    return payload.getPayloadJson();
                })
                .orElse(MissingNode.getInstance());
    }

    private MatchMetadata metadata(JsonNode summary, MatchEntity match) {
        if (summary != null && !summary.isMissingNode() && !summary.isNull()) {
            return metadataMapper.fromSummary(summary);
        }
        return new MatchMetadata(
                match.getProviderMatchId(),
                match.getSeasonId(),
                match.getCompetitionId(),
                new TeamMetadata(match.getHomeTeamId(), null, TeamSide.HOME),
                new TeamMetadata(match.getAwayTeamId(), null, TeamSide.AWAY),
                match.getStartTime(),
                0,
                0,
                "unknown",
                "unknown"
        );
    }

    private List<String> coverageReasonsFromLatestState(UUID matchId) {
        Optional<MatchStateEntity> latest = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(matchId);
        if (latest.isEmpty()) {
            return List.of("rebuilt from stored match coverage mode");
        }
        JsonNode reasons = latest.get().getStateJson().path("coverageReasons");
        if (!reasons.isArray()) {
            return List.of("rebuilt from stored match coverage mode");
        }
        List<String> values = new ArrayList<>();
        reasons.forEach(reason -> values.add(reason.asText()));
        return values.isEmpty() ? List.of("rebuilt from stored match coverage mode") : values;
    }

    private MatchStateView stateView(MatchEntity match, MatchStateEntity state) {
        return new MatchStateView(
                match.getId(),
                state.getEvent() == null ? null : state.getEvent().getId(),
                match.getProviderMatchId(),
                match.getCoverageMode(),
                state.getVersion(),
                team(match.getHomeTeamId(), state.getStateJson().path("teams").path("home").path("name").asText(null)),
                team(match.getAwayTeamId(), state.getStateJson().path("teams").path("away").path("name").asText(null)),
                state.getMinute(),
                state.getHomeScore(),
                state.getAwayScore(),
                state.getHomeRedCards(),
                state.getAwayRedCards(),
                objectMapper.convertValue(state.getStateJson(), new TypeReference<Map<String, Object>>() {
                }),
                state.getUpdatedAt()
        );
    }

    private FeatureSnapshotView featureView(FeatureSnapshotEntity snapshot) {
        MatchEventEntity event = snapshot.getEvent();
        return new FeatureSnapshotView(
                snapshot.getId(),
                snapshot.getMatch().getId(),
                event == null ? null : event.getId(),
                event == null ? null : event.getEventSequence(),
                snapshot.getMinute(),
                snapshot.getCoverageMode(),
                snapshot.getFeatureSetVersion(),
                objectMapper.convertValue(snapshot.getFeaturesJson(), new TypeReference<Map<String, Object>>() {
                }),
                objectMapper.convertValue(snapshot.getAvailabilityJson(), new TypeReference<Map<String, Object>>() {
                }),
                snapshot.getCreatedAt()
        );
    }

    private TeamView team(String id, String name) {
        return new TeamView(id, name);
    }

    public record StoredPayloadContext(
            JsonNode summary,
            JsonNode lineups,
            JsonNode momentum,
            JsonNode extendedSummary,
            JsonNode seasonInfo,
            JsonNode standings,
            JsonNode formStandings,
            JsonNode seasonProbabilities,
            Map<String, UUID> payloadIds
    ) {
    }
}
