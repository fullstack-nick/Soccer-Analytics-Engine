package com.example.sportsanalytics.sportradar.cache;

import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.RawPayloadRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarHttpGateway;
import com.example.sportsanalytics.sportradar.client.SportradarHttpResponse;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.example.sportsanalytics.sportradar.client.SportradarUriFactory;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CachedSportradarClient implements SportradarClient {
    private final RawPayloadRepository rawPayloadRepository;
    private final SportradarHttpGateway httpGateway;
    private final SportradarUriFactory uriFactory;
    private final Clock clock;

    @Autowired
    public CachedSportradarClient(
            RawPayloadRepository rawPayloadRepository,
            SportradarHttpGateway httpGateway,
            SportradarUriFactory uriFactory
    ) {
        this(rawPayloadRepository, httpGateway, uriFactory, Clock.systemUTC());
    }

    CachedSportradarClient(
            RawPayloadRepository rawPayloadRepository,
            SportradarHttpGateway httpGateway,
            SportradarUriFactory uriFactory,
            Clock clock
    ) {
        this.rawPayloadRepository = rawPayloadRepository;
        this.httpGateway = httpGateway;
        this.uriFactory = uriFactory;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SportradarPayload fetch(SportradarEndpoint endpoint, String providerId, boolean forceRefresh) {
        String requestPath = uriFactory.requestPath(endpoint, providerId);
        if (!forceRefresh) {
            Instant now = clock.instant();
            return rawPayloadRepository
                    .findFirstByProviderIdAndSourceEndpointAndRequestPathAndExpiresAtAfterOrderByFetchedAtDesc(
                            providerId,
                            endpoint.sourceEndpoint(),
                            requestPath,
                            now
                    )
                    .map(this::fromCache)
                    .orElseGet(() -> fetchAndStore(endpoint, providerId));
        }
        return fetchAndStore(endpoint, providerId);
    }

    private SportradarPayload fetchAndStore(SportradarEndpoint endpoint, String providerId) {
        SportradarHttpResponse response = httpGateway.fetch(endpoint, providerId);

        RawPayloadEntity entity = new RawPayloadEntity();
        entity.setSourceEndpoint(endpoint.sourceEndpoint());
        entity.setProviderId(providerId);
        entity.setFetchedAt(response.fetchedAt());
        entity.setHttpStatus(response.httpStatus());
        entity.setCacheControl(response.cacheControl());
        entity.setExpiresAt(response.expiresAt());
        entity.setProviderPackage(response.providerPackage());
        entity.setRequestPath(response.requestPath());
        entity.setPayloadJson(response.payload());

        return fromStored(rawPayloadRepository.save(entity), false);
    }

    private SportradarPayload fromCache(RawPayloadEntity entity) {
        return fromStored(entity, true);
    }

    private SportradarPayload fromStored(RawPayloadEntity entity, boolean fromCache) {
        return new SportradarPayload(
                entity.getId(),
                endpointFromSource(entity.getSourceEndpoint()),
                entity.getProviderId(),
                entity.getRequestPath(),
                entity.getHttpStatus(),
                entity.getCacheControl(),
                entity.getFetchedAt(),
                entity.getExpiresAt(),
                entity.getProviderPackage(),
                fromCache,
                entity.getPayloadJson()
        );
    }

    private static SportradarEndpoint endpointFromSource(String sourceEndpoint) {
        for (SportradarEndpoint endpoint : SportradarEndpoint.values()) {
            if (endpoint.sourceEndpoint().equals(sourceEndpoint)) {
                return endpoint;
            }
        }
        return SportradarEndpoint.SPORT_EVENT_SUMMARY;
    }
}
