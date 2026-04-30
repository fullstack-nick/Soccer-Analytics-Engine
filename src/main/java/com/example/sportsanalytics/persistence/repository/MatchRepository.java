package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.MatchEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {
    Optional<MatchEntity> findByProviderMatchId(String providerMatchId);
}
