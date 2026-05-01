package com.example.sportsanalytics.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

    @Valid
    private Live live = new Live();

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
        private long requestDelayMs = 1100;

        @Min(0)
        private int maxRetries = 2;
    }

    @Getter
    @Setter
    public static class Live {
        private boolean enabled = false;

        @Min(1000)
        private long pollDelayMs = 10_000;

        @Min(1000)
        private long fullTimelineRefreshMs = 60_000;

        @Min(1)
        private int maxMatchesPerTick = 3;

        @DecimalMin("0.0")
        private double divergenceAlertThreshold = 0.12;

        @DecimalMin("0.0")
        private double redCardSwingThreshold = 0.10;

        @DecimalMin("0.0")
        private double pressureAlertThreshold = 2.5;

        @DecimalMin("0.0")
        private double xgContradictionThreshold = 0.7;

        @Min(0)
        private int lateMomentumMinute = 70;

        @DecimalMin("0.0")
        private double lateMomentumThreshold = 15.0;
    }
}
