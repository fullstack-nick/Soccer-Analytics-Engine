package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.MatchEventEntity;
import com.example.sportsanalytics.domain.model.MatchEventType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchEventRepository extends JpaRepository<MatchEventEntity, UUID> {
    @Query("select count(e) > 0 from MatchEventEntity e where e.match.id = :matchId and e.providerEventId = :providerEventId")
    boolean existsByMatchIdAndProviderEventId(@Param("matchId") UUID matchId, @Param("providerEventId") String providerEventId);

    @Query("select e from MatchEventEntity e where e.match.id = :matchId and e.providerEventId = :providerEventId")
    Optional<MatchEventEntity> findByMatchIdAndProviderEventId(
            @Param("matchId") UUID matchId,
            @Param("providerEventId") String providerEventId
    );

    @Query("select e from MatchEventEntity e where e.match.id = :matchId order by e.eventSequence asc")
    List<MatchEventEntity> findByMatchIdOrderByEventSequenceAsc(@Param("matchId") UUID matchId);

    @Query("select e from MatchEventEntity e where e.match.id = :matchId and e.eventType = :eventType order by e.eventSequence asc")
    List<MatchEventEntity> findByMatchIdAndEventTypeOrderByEventSequenceAsc(
            @Param("matchId") UUID matchId,
            @Param("eventType") MatchEventType eventType
    );
}
