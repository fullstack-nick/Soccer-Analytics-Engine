package com.example.sportsanalytics.sportradar.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.RawPayloadRepository;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarHttpGateway;
import com.example.sportsanalytics.sportradar.client.SportradarHttpResponse;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.example.sportsanalytics.sportradar.client.SportradarUriFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CachedSportradarClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void usesFreshCachedPayloadUnlessForceRefreshIsRequested() throws Exception {
        RawPayloadRepository repository = mock(RawPayloadRepository.class);
        SportradarHttpGateway gateway = mock(SportradarHttpGateway.class);
        CachedSportradarClient client = new CachedSportradarClient(repository, gateway, uriFactory(), clock);
        RawPayloadEntity cached = cachedEntity();
        when(repository.findFirstByProviderIdAndSourceEndpointAndRequestPathAndExpiresAtAfterOrderByFetchedAtDesc(
                "sr:sport_event:1",
                "summary",
                "/soccer-extended/trial/v4/en/sport_events/sr:sport_event:1/summary.json",
                clock.instant()
        )).thenReturn(Optional.of(cached));

        SportradarPayload payload = client.fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1", false);

        assertThat(payload.fromCache()).isTrue();
        assertThat(payload.payload().path("cached").asBoolean()).isTrue();
        verify(gateway, never()).fetch(any(), any());
    }

    @Test
    void bypassesCacheWhenForceRefreshIsRequested() throws Exception {
        RawPayloadRepository repository = mock(RawPayloadRepository.class);
        SportradarHttpGateway gateway = mock(SportradarHttpGateway.class);
        CachedSportradarClient client = new CachedSportradarClient(repository, gateway, uriFactory(), clock);
        when(gateway.fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1"))
                .thenReturn(new SportradarHttpResponse(
                        SportradarEndpoint.SPORT_EVENT_SUMMARY,
                        "sr:sport_event:1",
                        "/soccer-extended/trial/v4/en/sport_events/sr:sport_event:1/summary.json",
                        200,
                        "max-age=30, public",
                        clock.instant(),
                        clock.instant().plusSeconds(30),
                        "soccer-extended",
                        objectMapper.readTree("{\"fresh\":true}")
                ));
        when(repository.save(any(RawPayloadEntity.class))).thenAnswer(invocation -> {
            RawPayloadEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SportradarPayload payload = client.fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1", true);

        assertThat(payload.fromCache()).isFalse();
        assertThat(payload.payload().path("fresh").asBoolean()).isTrue();
        verify(repository, never()).findFirstByProviderIdAndSourceEndpointAndRequestPathAndExpiresAtAfterOrderByFetchedAtDesc(
                any(), any(), any(), any()
        );
    }

    private RawPayloadEntity cachedEntity() throws Exception {
        RawPayloadEntity entity = new RawPayloadEntity();
        entity.setId(UUID.randomUUID());
        entity.setSourceEndpoint("summary");
        entity.setProviderId("sr:sport_event:1");
        entity.setFetchedAt(clock.instant());
        entity.setHttpStatus(200);
        entity.setCacheControl("max-age=30, public");
        entity.setExpiresAt(clock.instant().plusSeconds(30));
        entity.setProviderPackage("soccer-extended");
        entity.setRequestPath("/soccer-extended/trial/v4/en/sport_events/sr:sport_event:1/summary.json");
        entity.setPayloadJson(objectMapper.readTree("{\"cached\":true}"));
        return entity;
    }

    private SportradarUriFactory uriFactory() {
        SportsAnalyticsProperties properties = new SportsAnalyticsProperties();
        properties.getSportradar().setRequestDelayMs(0);
        return new SportradarUriFactory(properties);
    }
}
