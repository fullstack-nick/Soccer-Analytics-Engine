package com.example.sportsanalytics.sportradar.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record SportradarPayload(
        UUID rawPayloadId,
        SportradarEndpoint endpoint,
        String providerId,
        String requestPath,
        int httpStatus,
        String cacheControl,
        Instant fetchedAt,
        Instant expiresAt,
        String providerPackage,
        boolean fromCache,
        JsonNode payload
) {
}
