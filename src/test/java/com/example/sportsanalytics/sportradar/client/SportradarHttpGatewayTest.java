package com.example.sportsanalytics.sportradar.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SportradarHttpGatewayTest {
    private static final String SUMMARY_PATH_REGEX =
            "/soccer-extended/trial/v4/en/sport_events/sr(%3A|:)sport_event(%3A|:)1/summary\\.json";

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void apiKeyIsRequiredOnlyWhenCallingSportradar() {
        SportsAnalyticsProperties properties = SportradarUriFactoryTest.properties();
        properties.getSportradar().setApiKey("");

        SportradarHttpGateway gateway = new SportradarHttpGateway(
                properties,
                new SportradarUriFactory(properties),
                RestClient.builder().build(),
                new ObjectMapper(),
                java.time.Clock.systemUTC()
        );

        assertThatThrownBy(() -> gateway.fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1"))
                .isInstanceOf(MissingSportradarApiKeyException.class);
    }

    @Test
    void requestsJsonSuffixedEndpointAndStoresSanitizedRequestPath() {
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .withQueryParam("api_key", equalTo("secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Cache-Control", "max-age=30, public")
                        .withBody("{\"ok\":true}")));

        SportradarHttpResponse response = gatewayForServer(1).fetch(
                SportradarEndpoint.SPORT_EVENT_SUMMARY,
                "sr:sport_event:1"
        );

        assertThat(response.payload().path("ok").asBoolean()).isTrue();
        assertThat(response.requestPath())
                .isEqualTo("/soccer-extended/trial/v4/en/sport_events/sr:sport_event:1/summary.json");
        assertThat(response.requestPath()).doesNotContain("api_key").doesNotContain("secret");
        assertThat(response.expiresAt()).isAfter(response.fetchedAt());
        server.verify(1, getRequestedFor(urlPathMatching(SUMMARY_PATH_REGEX))
                .withQueryParam("api_key", equalTo("secret")));
    }

    @Test
    void retriesRateLimitedResponses() {
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .inScenario("rate-limit")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"rate\"}"))
                .willSetStateTo("retry"));
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .inScenario("rate-limit")
                .whenScenarioStateIs("retry")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        SportradarHttpResponse response = gatewayForServer(1).fetch(
                SportradarEndpoint.SPORT_EVENT_SUMMARY,
                "sr:sport_event:1"
        );

        assertThat(response.payload().path("ok").asBoolean()).isTrue();
        server.verify(2, getRequestedFor(urlPathMatching(SUMMARY_PATH_REGEX)));
    }

    @Test
    void doesNotRetryNotFoundResponses() {
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"missing\"}")));

        assertThatThrownBy(() -> gatewayForServer(2).fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1"))
                .isInstanceOf(SportradarNotFoundException.class);
        server.verify(1, getRequestedFor(urlPathMatching(SUMMARY_PATH_REGEX)));
    }

    @Test
    void retriesServerErrorsAndThrowsWhenExhausted() {
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"upstream\"}")));

        assertThatThrownBy(() -> gatewayForServer(2).fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1"))
                .isInstanceOf(SportradarUpstreamException.class);
        server.verify(3, getRequestedFor(urlPathMatching(SUMMARY_PATH_REGEX)));
    }

    @Test
    void retriesTransportFailuresAndThrowsWhenExhausted() {
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> gatewayForServer(1).fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1"))
                .isInstanceOf(SportradarUpstreamException.class);
        server.verify(2, getRequestedFor(urlPathMatching(SUMMARY_PATH_REGEX)));
    }

    @Test
    void acceptsPayloadsWithMissingOptionalFields() {
        server.stubFor(get(urlPathMatching(SUMMARY_PATH_REGEX))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        SportradarHttpResponse response = gatewayForServer(0).fetch(
                SportradarEndpoint.SPORT_EVENT_SUMMARY,
                "sr:sport_event:1"
        );

        assertThat(response.payload().isObject()).isTrue();
        assertThat(response.payload()).isEmpty();
    }

    private SportradarHttpGateway gatewayForServer(int maxRetries) {
        SportsAnalyticsProperties properties = SportradarUriFactoryTest.properties();
        properties.getSportradar().setApiKey("secret");
        properties.getSportradar().setBaseUrl(server.baseUrl());
        properties.getSportradar().setRequestDelayMs(0);
        properties.getSportradar().setMaxRetries(maxRetries);
        return new SportradarHttpGateway(
                properties,
                new SportradarUriFactory(properties),
                RestClient.builder().build(),
                new ObjectMapper(),
                java.time.Clock.systemUTC()
        );
    }
}
