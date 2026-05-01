package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.domain.model.LiveTrackingStatus;
import com.example.sportsanalytics.persistence.entity.LiveMatchTrackingEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveMatchTrackingRepository extends JpaRepository<LiveMatchTrackingEntity, UUID> {
    Optional<LiveMatchTrackingEntity> findByMatch_Id(UUID matchId);

    List<LiveMatchTrackingEntity> findByActiveTrueAndTrackingStatusOrderByUpdatedAtAsc(LiveTrackingStatus trackingStatus);

    List<LiveMatchTrackingEntity> findByActiveTrueOrderByUpdatedAtAsc();

    List<LiveMatchTrackingEntity> findByOrderByUpdatedAtDesc();
}
