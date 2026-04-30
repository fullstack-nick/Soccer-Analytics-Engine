package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.MatchStateEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchStateRepository extends JpaRepository<MatchStateEntity, UUID> {
    Optional<MatchStateEntity> findFirstByMatch_IdOrderByVersionDesc(UUID matchId);
}
