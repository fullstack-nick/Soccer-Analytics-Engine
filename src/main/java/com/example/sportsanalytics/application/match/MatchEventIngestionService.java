package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.application.match.dto.EventWriteCounts;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.MatchEventRepository;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchEventIngestionService {
    private final MatchEventRepository matchEventRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public MatchEventIngestionService(
            MatchEventRepository matchEventRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper
    ) {
        this(matchEventRepository, entityManager, objectMapper, Clock.systemUTC());
    }

    MatchEventIngestionService(
            MatchEventRepository matchEventRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.matchEventRepository = matchEventRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public EventWriteCounts upsertEvents(MatchEntity match, List<NormalizedTimelineEvent> events) {
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
            mergeEvent(entity, normalized, now, existing.isEmpty());
            matchEventRepository.save(entity);
        }
        return new EventWriteCounts(inserted, updated);
    }

    private void mergeEvent(
            MatchEventEntity entity,
            NormalizedTimelineEvent normalized,
            Instant receivedAt,
            boolean newEntity
    ) {
        TimelineSourceType existingSource = sourceOrUnknown(entity.getSourceTimelineType());
        TimelineSourceType incomingSource = sourceOrUnknown(normalized.sourceTimelineType());
        boolean incomingAtLeastAsRich = sourceRank(incomingSource) >= sourceRank(existingSource);

        entity.setEventSequence(normalized.sequence());
        entity.setOccurredAtMinute(normalized.minute());
        entity.setReceivedAt(receivedAt);

        if (newEntity || incomingAtLeastAsRich || isBlank(entity.getProviderEventType())) {
            entity.setProviderEventType(firstNonBlank(normalized.providerEventType(), entity.getProviderEventType()));
        }
        entity.setEventType(mergeEventType(entity.getEventType(), entity.isScoreChanged(), normalized.eventType()));

        if (newEntity || normalized.stoppageTime() != null) {
            entity.setStoppageTime(normalized.stoppageTime());
        }
        entity.setTeamSide(mergeTeamSide(entity.getTeamSide(), normalized.teamSide()));
        if (newEntity || incomingAtLeastAsRich) {
            entity.setPayload(rawPayloadReference(normalized.rawPayloadId()));
        }

        JsonNode incomingPlayerIds = objectMapper.valueToTree(normalized.playerIds());
        if (newEntity || hasValues(incomingPlayerIds) || !hasValues(entity.getPlayerIds())) {
            entity.setPlayerIds(incomingPlayerIds);
        }

        entity.setX(mergeNullable(entity.getX(), normalized.x()));
        entity.setY(mergeNullable(entity.getY(), normalized.y()));
        entity.setDestinationX(mergeNullable(entity.getDestinationX(), normalized.destinationX()));
        entity.setDestinationY(mergeNullable(entity.getDestinationY(), normalized.destinationY()));
        entity.setXgValue(mergeNullable(entity.getXgValue(), normalized.xgValue()));
        entity.setOutcome(firstNonBlank(normalized.outcome(), entity.getOutcome()));
        entity.setHomeScoreAfter(mergeNullable(entity.getHomeScoreAfter(), normalized.homeScoreAfter()));
        entity.setAwayScoreAfter(mergeNullable(entity.getAwayScoreAfter(), normalized.awayScoreAfter()));
        entity.setScoreChanged(entity.isScoreChanged() || normalized.scoreChanged());
        entity.setSourceTimelineType(richerSource(existingSource, incomingSource));
    }

    private MatchEventType mergeEventType(MatchEventType existing, boolean existingScoreChanged, MatchEventType incoming) {
        MatchEventType current = existing == null ? MatchEventType.UNKNOWN : existing;
        MatchEventType next = incoming == null ? MatchEventType.UNKNOWN : incoming;
        if (existingScoreChanged && current == MatchEventType.GOAL && next != MatchEventType.GOAL) {
            return current;
        }
        if (next == MatchEventType.UNKNOWN && current != MatchEventType.UNKNOWN) {
            return current;
        }
        return next;
    }

    private TeamSide mergeTeamSide(TeamSide existing, TeamSide incoming) {
        TeamSide current = existing == null ? TeamSide.UNKNOWN : existing;
        TeamSide next = incoming == null ? TeamSide.UNKNOWN : incoming;
        if (next == TeamSide.UNKNOWN && current != TeamSide.UNKNOWN) {
            return current;
        }
        return next;
    }

    private <T> T mergeNullable(T existing, T incoming) {
        return incoming == null ? existing : incoming;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    private boolean hasValues(JsonNode node) {
        return node != null && node.isArray() && node.size() > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private TimelineSourceType richerSource(TimelineSourceType existing, TimelineSourceType incoming) {
        return sourceRank(incoming) >= sourceRank(existing) ? incoming : existing;
    }

    private TimelineSourceType sourceOrUnknown(TimelineSourceType source) {
        return source == null ? TimelineSourceType.UNKNOWN : source;
    }

    private int sourceRank(TimelineSourceType source) {
        return switch (sourceOrUnknown(source)) {
            case EXTENDED -> 5;
            case LIVE_TIMELINE -> 4;
            case LIVE_DELTA -> 3;
            case STANDARD -> 2;
            case SUMMARY_ONLY -> 1;
            case UNKNOWN -> 0;
        };
    }

    private RawPayloadEntity rawPayloadReference(UUID rawPayloadId) {
        if (rawPayloadId == null) {
            return null;
        }
        return entityManager.getReference(RawPayloadEntity.class, rawPayloadId);
    }
}
