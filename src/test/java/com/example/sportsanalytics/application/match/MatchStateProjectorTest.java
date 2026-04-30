package com.example.sportsanalytics.application.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
import com.example.sportsanalytics.sportradar.mapping.CoverageDetectionResult;
import com.example.sportsanalytics.sportradar.mapping.MatchEventTypeMapper;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadataMapper;
import com.example.sportsanalytics.sportradar.mapping.NormalizedTimelineEvent;
import com.example.sportsanalytics.sportradar.mapping.SportradarEventNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchStateProjectorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void projectsScoreMinuteRedCardsAndProviderContext() throws Exception {
        JsonNode summary = fixture("summary");
        MatchMetadata metadata = new MatchMetadataMapper().fromSummary(summary);
        List<NormalizedTimelineEvent> events = new SportradarEventNormalizer(new MatchEventTypeMapper()).normalize(
                metadata.providerMatchId(),
                fixture("extended_timeline"),
                TimelineSourceType.EXTENDED,
                UUID.randomUUID()
        );
        MatchStateProjector projector = new MatchStateProjector(objectMapper);

        MatchStateProjection projection = projector.project(
                metadata,
                new CoverageDetectionResult(CoverageMode.RICH, List.of("test")),
                events,
                fixture("lineups"),
                fixture("momentum"),
                fixture("extended_summary"),
                fixture("season_info"),
                Map.of("summary", UUID.randomUUID())
        );

        assertThat(projection.minute()).isEqualTo(65);
        assertThat(projection.homeScore()).isEqualTo(1);
        assertThat(projection.awayScore()).isEqualTo(0);
        assertThat(projection.awayRedCards()).isEqualTo(1);
        assertThat(projection.stateJson().path("coverageMode").asText()).isEqualTo("RICH");
        assertThat(projection.stateJson().path("latestMomentum").path("value").asInt()).isEqualTo(-20);
        assertThat(projection.stateJson().path("lineups").path("available").asBoolean()).isTrue();
    }

    private JsonNode fixture(String name) throws Exception {
        return objectMapper.readTree(getClass().getResourceAsStream("/fixtures/sportradar/" + name + ".json"));
    }
}
