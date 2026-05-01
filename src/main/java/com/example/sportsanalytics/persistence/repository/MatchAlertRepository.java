package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.MatchAlertEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAlertRepository extends JpaRepository<MatchAlertEntity, UUID> {
    List<MatchAlertEntity> findByMatch_IdOrderByCreatedAtDesc(UUID matchId);

    boolean existsByMatch_IdAndDeduplicationKey(UUID matchId, String deduplicationKey);

    long countByMatch_Id(UUID matchId);
}
