package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.StoredMatchView;
import com.example.sportsanalytics.application.match.dto.TeamView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
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
import jakarta.persistence.EntityManager;
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
public class MatchTrackingService implements MatchTrackingUseCase {
    private final SportradarClient sportradarClient;
    private final MatchMetadataMapper metadataMapper;
    private final CoverageDetector coverageDetector;
    private final SportradarEventNormalizer eventNormalizer;
    private final MatchStateProjector stateProjector;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchStateRepository matchStateRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MatchTrackingService(
            SportradarClient sportradarClient,
            MatchMetadataMapper metadataMapper,
            CoverageDetector coverageDetector,
            SportradarEventNormalizer eventNormalizer,
            MatchStateProjector stateProjector,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchStateRepository matchStateRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper
    ) {
        this(sportradarClient, metadataMapper, coverageDetector, eventNormalizer, stateProjector, matchRepository,
                matchEventRepository, matchStateRepository, entityManager, objectMapper, Clock.systemUTC());
    }

    MatchTrackingService(
            SportradarClient sportradarClient,
            MatchMetadataMapper metadataMapper,
            CoverageDetector coverageDetector,
            SportradarEventNormalizer eventNormalizer,
            MatchStateProjector stateProjector,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchStateRepository matchStateRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.sportradarClient = sportradarClient;
        this.metadataMapper = metadataMapper;
        this.coverageDetector = coverageDetector;
        this.eventNormalizer = eventNormalizer;
        this.stateProjector = stateProjector;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.matchStateRepository = matchStateRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
        EventWriteCounts counts = upsertEvents(match, normalizedEvents);

        MatchStateProjection projection = stateProjector.project(
                metadata,
                coverage,
                normalizedEvents,
                lineups,
                momentum,
                extendedSummary,
                seasonInfo,
                fetch.payloadIds()
        );
        long stateVersion = persistState(match, projection);

        return new TrackMatchResult(
                match.getId(),
                match.getProviderMatchId(),
                match.getCoverageMode(),
                stateVersion,
                team(metadata.homeTeam().id(), metadata.homeTeam().name()),
                team(metadata.awayTeam().id(), metadata.awayTeam().name()),
                projection.minute(),
                projection.homeScore(),
                projection.awayScore(),
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
        if (result.payload(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE).isMissingNode()) {
            fetchOptional(result, SportradarEndpoint.SPORT_EVENT_TIMELINE, sportEventId, forceRefresh);
        }
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_LINEUPS, sportEventId, forceRefresh);
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_MOMENTUM, sportEventId, forceRefresh);
        fetchOptional(result, SportradarEndpoint.SPORT_EVENT_EXTENDED_SUMMARY, sportEventId, forceRefresh);

        MatchMetadata metadata = metadataMapper.fromSummary(result.required(SportradarEndpoint.SPORT_EVENT_SUMMARY).payload());
        if (!"UNKNOWN".equals(metadata.seasonId())) {
            fetchOptional(result, SportradarEndpoint.SEASON_INFO, metadata.seasonId(), forceRefresh);
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
        SportradarPayload selected = fetch.payloadByEndpoint(SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE)
                .or(() -> fetch.payloadByEndpoint(SportradarEndpoint.SPORT_EVENT_TIMELINE))
                .orElse(null);
        if (selected == null) {
            return List.of();
        }
        TimelineSourceType sourceType = selected.endpoint() == SportradarEndpoint.SPORT_EVENT_EXTENDED_TIMELINE
                ? TimelineSourceType.EXTENDED
                : TimelineSourceType.STANDARD;
        return eventNormalizer.normalize(providerMatchId, selected.payload(), sourceType, selected.rawPayloadId());
    }

    private EventWriteCounts upsertEvents(MatchEntity match, List<NormalizedTimelineEvent> events) {
        int inserted = 0;
        int updated = 0;
        Instant now = clock.instant();
        for (NormalizedTimelineEvent normalized : events) {
            Optional<MatchEventEntity> existing = matchEventRepository.findByMatchIdAndProviderEventId(
                    match.getId(),
                    normalized.providerEventId()
            );
            MatchEventEntity entity = existing.orElseGet(MatchEventEntity::new);
            if (existing.isEmpty()) {
                inserted++;
                entity.setMatch(match);
                entity.setProviderEventId(normalized.providerEventId());
            } else {
                updated++;
            }
            entity.setProviderEventType(normalized.providerEventType());
            entity.setEventSequence(normalized.sequence());
            entity.setEventType(normalized.eventType());
            entity.setOccurredAtMinute(normalized.minute());
            entity.setStoppageTime(normalized.stoppageTime());
            entity.setTeamSide(normalized.teamSide());
            entity.setPayload(rawPayloadReference(normalized.rawPayloadId()));
            entity.setPlayerIds(objectMapper.valueToTree(normalized.playerIds()));
            entity.setX(normalized.x());
            entity.setY(normalized.y());
            entity.setDestinationX(normalized.destinationX());
            entity.setDestinationY(normalized.destinationY());
            entity.setXgValue(normalized.xgValue());
            entity.setOutcome(normalized.outcome());
            entity.setHomeScoreAfter(normalized.homeScoreAfter());
            entity.setAwayScoreAfter(normalized.awayScoreAfter());
            entity.setScoreChanged(normalized.scoreChanged());
            entity.setSourceTimelineType(normalized.sourceTimelineType());
            entity.setReceivedAt(now);
            matchEventRepository.save(entity);
        }
        return new EventWriteCounts(inserted, updated);
    }

    private RawPayloadEntity rawPayloadReference(UUID rawPayloadId) {
        if (rawPayloadId == null) {
            return null;
        }
        return entityManager.getReference(RawPayloadEntity.class, rawPayloadId);
    }

    private long persistState(MatchEntity match, MatchStateProjection projection) {
        long nextVersion = matchStateRepository.findFirstByMatch_IdOrderByVersionDesc(match.getId())
                .map(MatchStateEntity::getVersion)
                .orElse(0L) + 1L;

        MatchStateEntity state = new MatchStateEntity();
        state.setMatch(match);
        state.setVersion(nextVersion);
        state.setMinute(projection.minute());
        state.setHomeScore(projection.homeScore());
        state.setAwayScore(projection.awayScore());
        state.setHomeRedCards(projection.homeRedCards());
        state.setAwayRedCards(projection.awayRedCards());
        state.setStateJson(projection.stateJson());
        state.setUpdatedAt(clock.instant());
        return matchStateRepository.save(state).getVersion();
    }

    private MatchStateView stateView(MatchEntity match, MatchStateEntity state) {
        return new MatchStateView(
                match.getId(),
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

    private record EventWriteCounts(int inserted, int updated) {
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
