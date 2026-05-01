package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.application.match.dto.EventWriteCounts;
import com.example.sportsanalytics.persistence.entity.MatchEntity;
import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.MatchEventRepository;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
