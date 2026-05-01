package com.example.sportsanalytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SportsAnalyticsPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsDefaultSportradarConfiguration() {
        contextRunner.run(context -> {
            SportsAnalyticsProperties properties = context.getBean(SportsAnalyticsProperties.class);

            assertThat(properties.getSportradar().getLocale()).isEqualTo("en");
            assertThat(properties.getSportradar().getAccessLevel()).isEqualTo("trial");
            assertThat(properties.getSportradar().getPackageName()).isEqualTo("soccer-extended");
            assertThat(properties.getSportradar().getBaseUrl()).isEqualTo("https://api.sportradar.com");
            assertThat(properties.getSportradar().getRequestDelayMs()).isEqualTo(1100);
            assertThat(properties.getSportradar().getMaxRetries()).isEqualTo(2);
            assertThat(properties.getLive().isEnabled()).isFalse();
            assertThat(properties.getLive().getPollDelayMs()).isEqualTo(10_000);
            assertThat(properties.getLive().getMaxMatchesPerTick()).isEqualTo(3);
        });
    }

    @EnableConfigurationProperties(SportsAnalyticsProperties.class)
    static class PropertiesConfiguration {
    }
}
