package com.example.sportsanalytics.sportradar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SportradarHttpGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
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
    void retriesRateLimitedResponses() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            byte[] body = (attempt == 1 ? "{\"error\":\"rate\"}" : "{\"ok\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Cache-Control", "max-age=30, public");
            exchange.sendResponseHeaders(attempt == 1 ? 429 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        SportradarHttpResponse response = gatewayForServer().fetch(
                SportradarEndpoint.SPORT_EVENT_SUMMARY,
                "sr:sport_event:1"
        );

        assertThat(attempts).hasValue(2);
        assertThat(response.payload().path("ok").asBoolean()).isTrue();
        assertThat(response.expiresAt()).isAfter(response.fetchedAt());
    }

    @Test
    void doesNotRetryNotFoundResponses() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            byte[] body = "{\"error\":\"missing\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> gatewayForServer().fetch(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:1"))
                .isInstanceOf(SportradarNotFoundException.class);
        assertThat(attempts).hasValue(1);
    }

    private SportradarHttpGateway gatewayForServer() {
        SportsAnalyticsProperties properties = SportradarUriFactoryTest.properties();
        properties.getSportradar().setApiKey("secret");
        properties.getSportradar().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.getSportradar().setRequestDelayMs(0);
        properties.getSportradar().setMaxRetries(1);
        return new SportradarHttpGateway(
                properties,
                new SportradarUriFactory(properties),
                RestClient.builder().build(),
                new ObjectMapper(),
                java.time.Clock.systemUTC()
        );
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", handler);
        server.start();
    }
}
