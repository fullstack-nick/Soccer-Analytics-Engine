package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureSnapshotRepository extends JpaRepository<FeatureSnapshotEntity, UUID> {
    @Query("""
            select f from FeatureSnapshotEntity f
            left join f.event e
            where f.match.id = :matchId
            order by f.minute asc, coalesce(e.eventSequence, 0) asc, f.createdAt asc
            """)
    List<FeatureSnapshotEntity> findByMatchIdOrderByTimeline(@Param("matchId") UUID matchId);

    @Query("""
            select f from FeatureSnapshotEntity f
            left join f.event e
            where f.match.id = :matchId
            order by f.minute desc, coalesce(e.eventSequence, 0) desc, f.createdAt desc
            """)
    List<FeatureSnapshotEntity> findLatestByMatchId(@Param("matchId") UUID matchId);

    default Optional<FeatureSnapshotEntity> findFirstLatestByMatchId(UUID matchId) {
        return findLatestByMatchId(matchId).stream().findFirst();
    }

    @Modifying
    @Query("delete from FeatureSnapshotEntity f where f.match.id = :matchId")
    void deleteByMatchId(@Param("matchId") UUID matchId);
}
