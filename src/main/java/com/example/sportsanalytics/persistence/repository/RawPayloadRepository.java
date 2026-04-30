package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawPayloadRepository extends JpaRepository<RawPayloadEntity, UUID> {
    Optional<RawPayloadEntity> findFirstByProviderIdAndSourceEndpointAndRequestPathAndExpiresAtAfterOrderByFetchedAtDesc(
            String providerId,
            String sourceEndpoint,
            String requestPath,
            Instant expiresAt
    );
}
