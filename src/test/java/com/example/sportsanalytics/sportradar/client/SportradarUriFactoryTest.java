package com.example.sportsanalytics.sportradar.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import org.junit.jupiter.api.Test;

class SportradarUriFactoryTest {
    @Test
    void encodesProviderUrnAndKeepsApiKeyOutOfSanitizedRequestPath() {
        SportsAnalyticsProperties properties = properties();
        SportradarUriFactory factory = new SportradarUriFactory(properties);

        String uri = factory.buildUri(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:70075140", "secret").toString();
        String requestPath = factory.requestPath(SportradarEndpoint.SPORT_EVENT_SUMMARY, "sr:sport_event:70075140");

        assertThat(uri).contains("/sport_events/sr%3Asport_event%3A70075140/summary");
        assertThat(uri).contains("api_key=secret");
        assertThat(requestPath).isEqualTo("/soccer-extended/trial/v4/en/sport_events/sr:sport_event:70075140/summary");
        assertThat(requestPath).doesNotContain("secret").doesNotContain("api_key");
    }

    static SportsAnalyticsProperties properties() {
        SportsAnalyticsProperties properties = new SportsAnalyticsProperties();
        properties.getSportradar().setBaseUrl("https://api.sportradar.com");
        properties.getSportradar().setPackageName("soccer-extended");
        properties.getSportradar().setAccessLevel("trial");
        properties.getSportradar().setLocale("en");
        properties.getSportradar().setRequestDelayMs(0);
        properties.getSportradar().setMaxRetries(1);
        return properties;
    }
}
