package com.example.sportsanalytics.sportradar.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CoverageDetectorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoverageDetector detector = new CoverageDetector();

    @Test
    void detectsRichCoverageFromExtendedTimeline() throws Exception {
        CoverageDetectionResult result = detector.detect(
                fixture("summary"),
                missing(),
                fixture("extended_timeline"),
                missing(),
                missing(),
                missing(),
                missing()
        );

        assertThat(result.mode()).isEqualTo(CoverageMode.RICH);
        assertThat(result.reasons()).isNotEmpty();
    }

    @Test
    void detectsStandardCoverageFromTimelineOnly() throws Exception {
        JsonNode timeline = objectMapper.readTree("{\"timeline\":{\"event\":[{\"id\":1,\"type\":\"goal\",\"match_time\":10}]}}");

        CoverageDetectionResult result = detector.detect(fixture("summary"), timeline, missing(), missing(), missing(), missing(), missing());

        assertThat(result.mode()).isEqualTo(CoverageMode.STANDARD);
    }

    @Test
    void detectsBasicCoverageFromSummaryOnly() throws Exception {
        CoverageDetectionResult result = detector.detect(fixture("summary"), missing(), missing(), missing(), missing(), missing(), missing());

        assertThat(result.mode()).isEqualTo(CoverageMode.BASIC);
    }

    private JsonNode fixture(String name) throws Exception {
        return objectMapper.readTree(getClass().getResourceAsStream("/fixtures/sportradar/" + name + ".json"));
    }

    private JsonNode missing() {
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
}
