package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.FeatureSnapshotEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureSnapshotRepository extends JpaRepository<FeatureSnapshotEntity, UUID> {
}
