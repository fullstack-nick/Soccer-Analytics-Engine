package com.example.sportsanalytics.sportradar.client;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SportradarUriFactory {
    private final SportsAnalyticsProperties properties;

    public SportradarUriFactory(SportsAnalyticsProperties properties) {
        this.properties = properties;
    }

    public URI buildUri(SportradarEndpoint endpoint, String providerId, String apiKey) {
        String baseUrl = properties.getSportradar().getBaseUrl().replaceAll("/+$", "");
        List<String> pathSegments = new ArrayList<>(List.of(
                properties.getSportradar().getPackageName(),
                properties.getSportradar().getAccessLevel(),
                "v4",
                properties.getSportradar().getLocale()
        ));
        pathSegments.addAll(endpoint.pathSegments(providerId));
        String encodedPath = pathSegments.stream()
                .map(this::encodeSegment)
                .reduce("", (left, right) -> left + "/" + right);
        String query = "api_key=" + encodeSegment(apiKey);
        return URI.create(baseUrl + encodedPath + "?" + query);
    }

    public String requestPath(SportradarEndpoint endpoint, String providerId) {
        List<String> base = List.of(
                properties.getSportradar().getPackageName(),
                properties.getSportradar().getAccessLevel(),
                "v4",
                properties.getSportradar().getLocale()
        );
        return "/" + String.join("/", base) + "/" + String.join("/", endpoint.pathSegments(providerId));
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
