package com.example.sportsanalytics.sportradar.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record SportradarHttpResponse(
        SportradarEndpoint endpoint,
        String providerId,
        String requestPath,
        int httpStatus,
        String cacheControl,
        Instant fetchedAt,
        Instant expiresAt,
        String providerPackage,
        JsonNode payload
) {
}
