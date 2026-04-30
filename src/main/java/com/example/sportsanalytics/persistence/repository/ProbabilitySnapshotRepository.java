package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.ProbabilitySnapshotEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProbabilitySnapshotRepository extends JpaRepository<ProbabilitySnapshotEntity, UUID> {
    Optional<ProbabilitySnapshotEntity> findFirstByMatchIdOrderByCreatedAtDesc(UUID matchId);
}
