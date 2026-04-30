package com.example.sportsanalytics.sportradar.client;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SportradarHttpGateway {
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private final SportsAnalyticsProperties properties;
    private final SportradarUriFactory uriFactory;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object delayMonitor = new Object();
    private Instant lastRequestAt;

    @Autowired
    public SportradarHttpGateway(
            SportsAnalyticsProperties properties,
            SportradarUriFactory uriFactory,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this(properties, uriFactory, restClientBuilder.build(), objectMapper, Clock.systemUTC());
    }

    SportradarHttpGateway(
            SportsAnalyticsProperties properties,
            SportradarUriFactory uriFactory,
            RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.uriFactory = uriFactory;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SportradarHttpResponse fetch(SportradarEndpoint endpoint, String providerId) {
        String apiKey = properties.getSportradar().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingSportradarApiKeyException();
        }

        URI uri = uriFactory.buildUri(endpoint, providerId, apiKey);
        String requestPath = uriFactory.requestPath(endpoint, providerId);
        int maxRetries = Math.max(0, properties.getSportradar().getMaxRetries());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                waitForRequestWindow();
                RawHttpResult result = request(uri);
                if (result.statusCode() >= 200 && result.statusCode() < 300) {
                    Instant fetchedAt = clock.instant();
                    String cacheControl = firstHeader(result.headers(), HttpHeaders.CACHE_CONTROL);
                    return new SportradarHttpResponse(
                            endpoint,
                            providerId,
                            requestPath,
                            result.statusCode(),
                            cacheControl,
                            fetchedAt,
                            expiresAt(fetchedAt, cacheControl),
                            properties.getSportradar().getPackageName(),
                            objectMapper.readTree(result.body())
                    );
                }
                if (result.statusCode() == 404) {
                    throw new SportradarNotFoundException(endpoint, providerId);
                }
                if (isRetryable(result.statusCode()) && attempt < maxRetries) {
                    continue;
                }
                throw new SportradarUpstreamException(endpoint, providerId, result.statusCode(), trimBody(result.body()));
            } catch (SportradarClientException exception) {
                throw exception;
            } catch (RestClientException | IOException exception) {
                if (attempt < maxRetries) {
                    continue;
                }
                throw new SportradarUpstreamException(endpoint, providerId, exception);
            }
        }

        throw new SportradarUpstreamException(endpoint, providerId, 0, "retry loop exhausted");
    }

    private RawHttpResult request(URI uri) {
        return restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> new RawHttpResult(
                        response.getStatusCode().value(),
                        response.getHeaders(),
                        StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)
                ));
    }

    private void waitForRequestWindow() {
        long delayMs = Math.max(0, properties.getSportradar().getRequestDelayMs());
        if (delayMs == 0) {
            lastRequestAt = clock.instant();
            return;
        }
        synchronized (delayMonitor) {
            Instant now = clock.instant();
            if (lastRequestAt != null) {
                long elapsed = Duration.between(lastRequestAt, now).toMillis();
                long waitMs = delayMs - elapsed;
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new SportradarClientException("Interrupted while respecting Sportradar request delay", exception);
                    }
                }
            }
            lastRequestAt = clock.instant();
        }
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private static Instant expiresAt(Instant fetchedAt, String cacheControl) {
        if (cacheControl == null) {
            return null;
        }
        Matcher matcher = MAX_AGE.matcher(cacheControl);
        if (!matcher.find()) {
            return null;
        }
        return fetchedAt.plusSeconds(Long.parseLong(matcher.group(1)));
    }

    private static String firstHeader(HttpHeaders headers, String name) {
        return headers.getFirst(name);
    }

    private static String trimBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.length() <= 240 ? body : body.substring(0, 240);
    }

    private record RawHttpResult(int statusCode, HttpHeaders headers, String body) {
    }
}
