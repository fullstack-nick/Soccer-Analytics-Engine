package com.example.sportsanalytics.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sports")
public class SportsAnalyticsProperties {

    @Valid
    private Sportradar sportradar = new Sportradar();

    @Getter
    @Setter
    public static class Sportradar {
        private String apiKey = "";

        @NotBlank
        private String locale = "en";

        @NotBlank
        private String accessLevel = "trial";

        @NotBlank
        private String packageName = "soccer-extended";

        @NotBlank
        private String baseUrl = "https://api.sportradar.com";

        @Min(0)
        private long requestDelayMs = 1500;

        @Min(0)
        private int maxRetries = 2;
    }
}
