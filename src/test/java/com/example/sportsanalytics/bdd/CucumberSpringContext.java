package com.example.sportsanalytics.bdd;

import com.example.sportsanalytics.SoccerIntelligenceEngineApplication;
import com.example.sportsanalytics.persistence.entity.RawPayloadEntity;
import com.example.sportsanalytics.persistence.repository.RawPayloadRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.client.SportradarClientException;
import com.example.sportsanalytics.sportradar.client.SportradarEndpoint;
import com.example.sportsanalytics.sportradar.client.SportradarNotFoundException;
import com.example.sportsanalytics.sportradar.client.SportradarPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.spring.CucumberContextConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(
        classes = {
                SoccerIntelligenceEngineApplication.class,
                CucumberSpringContext.CucumberTestConfiguration.class
        },
        properties = {
                "spring.docker.compose.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:soccer_intelligence_bdd;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=MINUTE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "sports.sportradar.api-key=test-key",
                "sports.sportradar.request-delay-ms=0",
                "sports.live.divergence-alert-threshold=0.12"
        }
)
@ActiveProfiles("test")
public class CucumberSpringContext {
    @TestConfiguration
    static class CucumberTestConfiguration {
        @Bean
        @Primary
        SportradarClient fixtureSportradarClient(
                ObjectMapper objectMapper,
                RawPayloadRepository rawPayloadRepository
        ) {
            return new FixtureSportradarClient(objectMapper, rawPayloadRepository);
        }
    }

    private static final class FixtureSportradarClient implements SportradarClient {
        private final ObjectMapper objectMapper;
        private final RawPayloadRepository rawPayloadRepository;

        private FixtureSportradarClient(ObjectMapper objectMapper, RawPayloadRepository rawPayloadRepository) {
            this.objectMapper = objectMapper;
            this.rawPayloadRepository = rawPayloadRepository;
        }

        @Override
        public SportradarPayload fetch(SportradarEndpoint endpoint, String providerId, boolean forceRefresh) {
            String resource = resource(endpoint, providerId);
            JsonNode payload = read(resource);
            RawPayloadEntity entity = new RawPayloadEntity();
            entity.setSourceEndpoint(endpoint.sourceEndpoint());
            entity.setProviderId(providerId);
            entity.setFetchedAt(Instant.parse("2026-04-30T00:00:00Z"));
            entity.setHttpStatus(200);
            entity.setCacheControl("max-age=30, public");
            entity.setExpiresAt(Instant.parse("2026-04-30T00:00:30Z"));
            entity.setProviderPackage("soccer-extended");
            entity.setRequestPath("/fixtures/sportradar/demo/" + resource);
            entity.setPayloadJson(payload);
            RawPayloadEntity saved = rawPayloadRepository.save(entity);
            return new SportradarPayload(
                    saved.getId(),
                    endpoint,
                    providerId,
                    saved.getRequestPath(),
                    saved.getHttpStatus(),
                    saved.getCacheControl(),
                    saved.getFetchedAt(),
                    saved.getExpiresAt(),
                    saved.getProviderPackage(),
                    false,
                    payload
            );
        }

        private String resource(SportradarEndpoint endpoint, String providerId) {
            if ("sr:sport_event:demo-rich".equals(providerId)) {
                return switch (endpoint) {
                    case SPORT_EVENT_SUMMARY -> "rich_summary.json";
                    case SPORT_EVENT_EXTENDED_TIMELINE -> "rich_extended_timeline.json";
                    case SPORT_EVENT_LINEUPS -> "rich_lineups.json";
                    case SPORT_EVENT_MOMENTUM -> "rich_momentum.json";
                    case SPORT_EVENT_EXTENDED_SUMMARY -> "rich_extended_summary.json";
                    default -> throw new SportradarNotFoundException(endpoint, providerId);
                };
            }
            if ("sr:season:demo-rich".equals(providerId)) {
                return switch (endpoint) {
                    case SEASON_INFO -> "rich_season_info.json";
                    case SEASON_STANDINGS -> "rich_standings.json";
                    case SEASON_FORM_STANDINGS -> "rich_form_standings.json";
                    case SEASON_PROBABILITIES -> "rich_season_probabilities.json";
                    default -> throw new SportradarNotFoundException(endpoint, providerId);
                };
            }
            if ("sr:sport_event:demo-basic".equals(providerId)
                    && endpoint == SportradarEndpoint.SPORT_EVENT_SUMMARY) {
                return "basic_summary.json";
            }
            throw new SportradarNotFoundException(endpoint, providerId);
        }

        private JsonNode read(String resource) {
            String path = "/fixtures/sportradar/demo/" + resource;
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                if (stream == null) {
                    throw new SportradarClientException("Missing fixture " + path);
                }
                return objectMapper.readTree(stream);
            } catch (IOException exception) {
                throw new SportradarClientException("Could not read fixture " + path, exception);
            }
        }
    }
}
