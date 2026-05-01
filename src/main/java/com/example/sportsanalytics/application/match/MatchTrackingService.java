package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.FeatureSnapshotView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.ProbabilityTimelinePoint;
import com.example.sportsanalytics.application.match.dto.RebuildMatchStateResult;
import com.example.sportsanalytics.application.match.dto.RebuildProbabilityResult;
import com.example.sportsanalytics.application.match.dto.EventWriteCounts;
import com.example.sportsanalytics.application.match.dto.FinalScoreView;
import com.example.sportsanalytics.application.match.dto.ReplayMatchResult;
import com.example.sportsanalytics.application.match.dto.StoredMatchView;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonResult;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.repository.MatchEventRepository;
import com.example.sportsanalytics.persistence.repository.MatchRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarClientException;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.example.sportsanalytics.sportradar.mapping.CoverageDetectionResult;
import com.example.sportsanalytics.sportradar.mapping.CoverageDetector;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadataMapper;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.example.sportsanalytics.sportradar.mapping.SportradarEventNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Clock;
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
public class MatchTrackingService implements MatchTrackingUseCase {
    private final SportradarClient sportradarClient;
    private final MatchMetadataMapper metadataMapper;
    private final CoverageDetector coverageDetector;
    private final SportradarEventNormalizer eventNormalizer;
    private final MatchEventIngestionService eventIngestionService;
    private final MatchStateRebuildService rebuildService;
    private final ProbabilityRebuildService probabilityRebuildService;
    private final AlertGenerationService alertGenerationService;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchStateRepository matchStateRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public MatchTrackingService(
            SportradarClient sportradarClient,
            MatchMetadataMapper metadataMapper,
            CoverageDetector coverageDetector,
            SportradarEventNormalizer eventNormalizer,
            MatchEventIngestionService eventIngestionService,
            MatchStateRebuildService rebuildService,
            ProbabilityRebuildService probabilityRebuildService,
            AlertGenerationService alertGenerationService,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchStateRepository matchStateRepository,
            ObjectMapper objectMapper
    ) {
        this(sportradarClient, metadataMapper, coverageDetector, eventNormalizer, eventIngestionService, rebuildService,
                probabilityRebuildService, alertGenerationService, matchRepository, matchEventRepository, matchStateRepository,
                objectMapper, Clock.systemUTC());
    }

    MatchTrackingService(
            SportradarClient sportradarClient,
            MatchMetadataMapper metadataMapper,
            CoverageDetector coverageDetector,
            SportradarEventNormalizer eventNormalizer,
            MatchEventIngestionService eventIngestionService,
            MatchStateRebuildService rebuildService,
            ProbabilityRebuildService probabilityRebuildService,
            AlertGenerationService alertGenerationService,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchStateRepository matchStateRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.sportradarClient = sportradarClient;
        this.metadataMapper = metadataMapper;
        this.coverageDetector = coverageDetector;
        this.eventNormalizer = eventNormalizer;
        this.eventIngestionService = eventIngestionService;
        this.rebuildService = rebuildService;
        this.probabilityRebuildService = probabilityRebuildService;
        this.alertGenerationService = alertGenerationService;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchStateRepository = matchStateRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TrackMatchResult track(TrackMatchCommand command) {
        TrackingFetchResult fetch = fetchMatchPayloads(command.sportEventId(), command.forceRefresh());
        MatchMetadata metadata = metadataMapper.fromSummary(fetch.required(SportradarEndpoint.SPORT_EVENT_SUMMARY).payload());

        JsonNode extendedTimeline = fetch.payload(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE);
        JsonNode timeline = fetch.payload(SportradarEndpoint.SPORT_EVENT_TIMELINE);
        JsonNode lineups = fetch.payload(SportradarEndpoint.SPORT_EVENT_LINEUPS);
        JsonNode momentum = fetch.payload(SportradarEndpoint.SPORT_EVENT_MOMENTUM);
        JsonNode extendedSummary = fetch.payload(SportradarEndpoint.SPORT_EVENT_EXTENDED_SUMMARY);
        JsonNode seasonInfo = fetch.payload(SportradarEndpoint.SEASON_INFO);
        JsonNode standings = fetch.payload(SportradarEndpoint.SEASON_STANDINGS);
        JsonNode formStandings = fetch.payload(SportradarEndpoint.SEASON_FORM_STANDINGS);
        JsonNode seasonProbabilities = fetch.payload(SportradarEndpoint.SEASON_PROBABILITIES);

        CoverageDetectionResult coverage = coverageDetector.detect(
                fetch.required(SportradarEndpoint.SPORT_EVENT_SUMMARY).payload(),
                timeline,
                extendedTimeline,
                lineups,
                momentum,
                extendedSummary,
                seasonInfo
        );

        MatchEntity match = upsertMatch(metadata, coverage);
        List<NormalizedTimelineEvent> normalizedEvents = normalizeSelectedTimeline(metadata.providerMatchId(), fetch);
        EventWriteCounts counts = eventIngestionService.upsertEvents(match, normalizedEvents);

        RebuildMatchStateResult rebuild = rebuildService.rebuild(
                match,
                metadata,
                coverage,
                rebuildService.payloadContextFromTracking(
                        fetch.required(SportradarEndpoint.SPORT_EVENT_SUMMARY).payload(),
                        lineups,
                        momentum,
                        extendedSummary,
                        seasonInfo,
                        standings,
                        formStandings,
                        seasonProbabilities,
                        fetch.payloadIds()
                )
        );
        alertGenerationService.generate(match.getId());
        MatchStateEntity latestState = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(match.getId())
                .orElseThrow(() -> new MatchNotFoundException(match.getId()));

        return new TrackMatchResult(
                match.getId(),
                match.getProviderMatchId(),
                match.getCoverageMode(),
                rebuild.latestStateVersion(),
                rebuild.stateSnapshotsCreated(),
                rebuild.featureSnapshotsCreated(),
                rebuild.probabilitySnapshotsCreated(),
                team(metadata.homeTeam().id(), metadata.homeTeam().name()),
                team(metadata.awayTeam().id(), metadata.awayTeam().name()),
                latestState.getMinute(),
                latestState.getHomeScore(),
                latestState.getAwayScore(),
                counts.inserted(),
                counts.updated(),
                fetch.fetchedEndpoints(),
                fetch.cachedEndpoints(),
                fetch.skippedOptionalEndpoints()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MatchStateView latestState(UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        MatchStateEntity state = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        return stateView(match, state);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchStateView> states(UUID matchId) {
        return rebuildService.states(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchEventView> events(UUID matchId, MatchEventType type) {
        if (!matchRepository.existsById(matchId)) {
            throw new MatchNotFoundException(matchId);
        }
        List<MatchEventEntity> events = type == null
                ? matchEventRepository.findByMatchIdOrderByEventSequenceAsc(matchId)
                : matchEventRepository.findByMatchIdAndEventTypeOrderByEventSequenceAsc(matchId, type);
        return events.stream().map(this::eventView).toList();
    }

    @Override
    @Transactional
    public RebuildMatchStateResult rebuildState(UUID matchId) {
        RebuildMatchStateResult result = rebuildService.rebuild(matchId);
        alertGenerationService.generate(matchId);
        return result;
    }

    @Override
    @Transactional
    public RebuildProbabilityResult rebuildProbabilities(UUID matchId) {
        RebuildProbabilityResult result = probabilityRebuildService.rebuild(matchId);
        alertGenerationService.generate(matchId);
        return result;
    }

    @Override
    @Transactional
    public ReplayMatchResult replay(UUID matchId, boolean forceRefresh) {
        MatchEntity match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        RebuildMatchStateResult rebuild;
        if (forceRefresh) {
            TrackMatchResult tracked = track(new TrackMatchCommand(match.getProviderMatchId(), true));
            rebuild = new RebuildMatchStateResult(
                    tracked.matchId(),
                    tracked.stateSnapshotsCreated(),
                    tracked.featureSnapshotsCreated(),
                    tracked.probabilitySnapshotsCreated(),
                    tracked.stateVersion()
            );
            match = matchRepository.findById(tracked.matchId()).orElseThrow(() -> new MatchNotFoundException(tracked.matchId()));
        } else {
            rebuild = rebuildService.rebuild(matchId);
        }
        MatchEntity currentMatch = match;
        MatchStateEntity latestState = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(currentMatch.getId())
                .orElseThrow(() -> new MatchNotFoundException(currentMatch.getId()));
        return new ReplayMatchResult(
                currentMatch.getId(),
                currentMatch.getProviderMatchId(),
                currentMatch.getCoverageMode(),
                matchEventRepository.findByMatchIdOrderByEventSequenceAsc(currentMatch.getId()).size(),
                rebuild.stateSnapshotsCreated(),
                rebuild.featureSnapshotsCreated(),
                rebuild.probabilitySnapshotsCreated(),
                new FinalScoreView(latestState.getHomeScore(), latestState.getAwayScore()),
                probabilityRebuildService.latestProbability(currentMatch.getId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureSnapshotView> features(UUID matchId) {
        return rebuildService.features(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public FeatureSnapshotView latestFeature(UUID matchId) {
        return rebuildService.latestFeature(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbabilitySnapshotView> probabilities(UUID matchId) {
        return probabilityRebuildService.probabilities(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProbabilitySnapshotView latestProbability(UUID matchId) {
        return probabilityRebuildService.latestProbability(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProbabilityTimelinePoint> probabilityTimeline(UUID matchId) {
        return probabilityRebuildService.probabilityTimeline(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public ModelComparisonResult modelComparison(UUID matchId) {
        return probabilityRebuildService.modelComparison(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredMatchView findByProviderMatchId(String providerMatchId) {
        MatchEntity match = matchRepository.findByProviderMatchId(providerMatchId)
                .orElseThrow(() -> new MatchNotFoundException(providerMatchId));
        return storedMatchView(match);
    }

    private TrackingFetchResult fetchMatchPayloads(String sportEventId, boolean forceRefresh) {
        TrackingFetchResult result = new TrackingFetchResult();
        result.add(sportradarClient.fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, sportEventId, forceRefresh));
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE, sportEventId, forceRefresh);
        if (result.payload(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE).isMissingNode()
                || !eventNormalizer.hasUsableMatchEvents(normalizeTimeline(
                        sportEventId,
                        result.payloadByEndpoint(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE).orElse(null)
                ))) {
            fetchOptional(result, SportradarEndpoint.SPORT_EVENT_TIMELINE, sportEventId, forceRefresh);
        }
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_LINEUPS, sportEventId, forceRefresh);
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_MOMENTUM, sportEventId, forceRefresh);
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_EXTENDED_SUMMARY, sportEventId, forceRefresh);

        MatchMetadata metadata = metadataMapper.fromSummary(result.required(SportradarEndpoint.SPORT_EVENT_SUMMARY).payload());
        if (!"UNKNOWN".equals(metadata.seasonId())) {
            fetchOptional(result, SportradarEndpoint.SEASON_INFO, metadata.seasonId(), forceRefresh);
            fetchOptional(result, SportradarEndpoint.SEASON_STANDINGS, metadata.seasonId(), forceRefresh);
            fetchOptional(result, SportradarEndpoint.SEASON_FORM_STANDINGS, metadata.seasonId(), forceRefresh);
            fetchOptional(result, SportradarEndpoint.SEASON_PROBABILITIES, metadata.seasonId(), forceRefresh);
        }
        return result;
    }

    private void fetchOptional(
            TrackingFetchResult result,
            SportradarEndpoint endpoint,
            String providerId,
            boolean forceRefresh
    ) {
        try {
            result.add(sportradarClient.fetch(endpoint, providerId, forceRefresh));
        } catch (SportradarClientException exception) {
            result.skip(endpoint, exception.getMessage());
        }
    }

    private MatchEntity upsertMatch(MatchMetadata metadata, CoverageDetectionResult coverage) {
        MatchEntity match = matchRepository.findByProviderMatchId(metadata.providerMatchId()).orElseGet(MatchEntity::new);
        match.setProviderMatchId(metadata.providerMatchId());
        match.setSeasonId(metadata.seasonId());
        match.setCompetitionId(metadata.competitionId());
        match.setHomeTeamId(metadata.homeTeam().id());
        match.setAwayTeamId(metadata.awayTeam().id());
        match.setStartTime(metadata.startTime());
        match.setCoverageMode(coverage.mode());
        return matchRepository.save(match);
    }

    private List<NormalizedTimelineEvent> normalizeSelectedTimeline(String providerMatchId, TrackingFetchResult fetch) {
        List<NormalizedTimelineEvent> extended = normalizeTimeline(
                providerMatchId,
                fetch.payloadByEndpoint(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE).orElse(null)
        );
        if (eventNormalizer.hasUsableMatchEvents(extended)) {
            return extended;
        }
        return normalizeTimeline(
                providerMatchId,
                fetch.payloadByEndpoint(SportradarEndpoint.SPORT_EVENT_TIMELINE).orElse(null)
        );
    }

    private List<NormalizedTimelineEvent> normalizeTimeline(String providerMatchId, SportradarPayload payload) {
        if (payload == null) {
            return List.of();
        }
        TimelineSourceType sourceType = payload.endpoint() == SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE
                ? TimelineSourceType.EXTENDED
                : TimelineSourceType.STANDARD;
        return eventNormalizer.normalize(providerMatchId, payload.payload(), sourceType, payload.rawPayloadId());
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

    private MatchEventView eventView(MatchEventEntity event) {
        return new MatchEventView(
                event.getId(),
                event.getMatch().getId(),
                event.getProviderEventId(),
                event.getProviderEventType(),
                event.getEventSequence(),
                event.getEventType(),
                event.getOccurredAtMinute(),
                event.getStoppageTime(),
                event.getTeamSide(),
                playerIds(event.getPlayerIds()),
                event.getX(),
                event.getY(),
                event.getDestinationX(),
                event.getDestinationY(),
                event.getXgValue(),
                event.getOutcome(),
                event.getHomeScoreAfter(),
                event.getAwayScoreAfter(),
                event.isScoreChanged(),
                event.getSourceTimelineType(),
                event.getReceivedAt()
        );
    }

    private List<String> playerIds(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        node.forEach(id -> ids.add(id.asText()));
        return ids;
    }

    private StoredMatchView storedMatchView(MatchEntity match) {
        return new StoredMatchView(
                match.getId(),
                match.getProviderMatchId(),
                match.getSeasonId(),
                match.getCompetitionId(),
                team(match.getHomeTeamId(), null),
                team(match.getAwayTeamId(), null),
                match.getStartTime(),
                match.getCoverageMode()
        );
    }

    private TeamView team(String id, String name) {
        return new TeamView(id, name);
    }

    private static final class TrackingFetchResult {
        private final Map<SportradarEndpoint, SportradarPayload> payloads = new LinkedHashMap<>();
        private final List<String> fetchedEndpoints = new ArrayList<>();
        private final List<String> cachedEndpoints = new ArrayList<>();
        private final List<String> skippedOptionalEndpoints = new ArrayList<>();

        void add(SportradarPayload payload) {
            payloads.put(payload.endpoint(), payload);
            if (payload.fromCache()) {
                cachedEndpoints.add(payload.endpoint().sourceEndpoint());
            } else {
                fetchedEndpoints.add(payload.endpoint().sourceEndpoint());
            }
        }

        void skip(SportradarEndpoint endpoint, String reason) {
            skippedOptionalEndpoints.add(endpoint.sourceEndpoint() + ": " + reason);
        }

        SportradarPayload required(SportradarEndpoint endpoint) {
            return payloads.get(endpoint);
        }

        JsonNode payload(SportradarEndpoint endpoint) {
            return payloadByEndpoint(endpoint)
                    .map(SportradarPayload::payload)
                    .orElse(com.fasterxml.jackson.databind.node.MissingNode.getInstance());
        }

        Optional<SportradarPayload> payloadByEndpoint(SportradarEndpoint endpoint) {
            return Optional.ofNullable(payloads.get(endpoint));
        }

        Map<String, UUID> payloadIds() {
            Map<String, UUID> ids = new LinkedHashMap<>();
            payloads.values().forEach(payload -> ids.put(payload.endpoint().sourceEndpoint(), payload.rawPayloadId()));
            return ids;
        }

        List<String> fetchedEndpoints() {
            return List.copyOf(fetchedEndpoints);
        }

        List<String> cachedEndpoints() {
            return List.copyOf(cachedEndpoints);
        }

        List<String> skippedOptionalEndpoints() {
            return List.copyOf(skippedOptionalEndpoints);
        }
    }
}
