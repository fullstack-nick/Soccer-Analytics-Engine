package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProbabilitySnapshotRepository extends JpaRepository<ProbabilitySnapshotEntity, UUID> {
    @Query("""
            select p from ProbabilitySnapshotEntity p
            left join fetch p.event e
            where p.match.id = :matchId
            order by p.minute asc, coalesce(e.eventSequence, 0) asc, p.createdAt asc
            """)
    List<ProbabilitySnapshotEntity> findByMatchIdOrderByTimeline(@Param("matchId") UUID matchId);

    @Query("""
            select p from ProbabilitySnapshotEntity p
            left join fetch p.event e
            where p.match.id = :matchId
            order by p.minute desc, coalesce(e.eventSequence, 0) desc, p.createdAt desc
            """)
    List<ProbabilitySnapshotEntity> findLatestByMatchId(@Param("matchId") UUID matchId);

    default Optional<ProbabilitySnapshotEntity> findFirstLatestByMatchId(UUID matchId) {
        return findLatestByMatchId(matchId).stream().findFirst();
    }

    @Modifying
    @Query("delete from ProbabilitySnapshotEntity p where p.match.id = :matchId")
    void deleteByMatchId(@Param("matchId") UUID matchId);
}
