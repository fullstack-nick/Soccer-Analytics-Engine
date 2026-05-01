package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchStateRepository extends JpaRepository<MatchStateEntity, UUID> {
    Optional<MatchStateEntity> findFirstByMatch_IdOrderByVersionDesc(UUID matchId);

    List<MatchStateEntity> findByMatch_IdOrderByVersionAsc(UUID matchId);

    @Query("""
            select s from MatchStateEntity s
            left join fetch s.event e
            where s.match.id = :matchId
            order by s.version asc
            """)
    List<MatchStateEntity> findByMatchIdOrderByVersionAscWithEvent(@Param("matchId") UUID matchId);

    @Modifying
    @Query("delete from MatchStateEntity s where s.match.id = :matchId")
    void deleteByMatchId(@Param("matchId") UUID matchId);
}
