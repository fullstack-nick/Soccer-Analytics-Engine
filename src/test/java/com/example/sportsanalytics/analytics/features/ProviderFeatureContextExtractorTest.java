package com.example.sportsanalytics.analytics.features;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.example.sportsanalytics.sportradar.mapping.TeamMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProviderFeatureContextExtractorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProviderFeatureContextExtractor extractor = new ProviderFeatureContextExtractor();

    @Test
    void extractsStrengthAndProviderProbabilityWhenAvailable() throws Exception {
        ProviderFeatureContext context = extractor.extract(
                metadata(),
                objectMapper.readTree("""
                        {
                          "standings": [
                            {"competitor": {"id": "home"}, "points": 30, "played": 10, "goals_for": 20, "goals_against": 10},
                            {"competitor": {"id": "away"}, "points": 20, "played": 10, "goals_for": 12, "goals_against": 15}
                          ]
                        }
                        """),
                objectMapper.readTree("""
                        {
                          "standings": [
                            {"competitor": {"id": "home"}, "form": "WWD"},
                            {"competitor": {"id": "away"}, "form": "LLD"}
                          ]
                        }
                        """),
                objectMapper.readTree("""
                        {
                          "sport_event_probabilities": [
                            {
                              "sport_event": {"id": "sr:sport_event:test"},
                              "markets": [
                                {
                                  "name": "winner",
                                  "outcomes": [
                                    {"name": "home", "probability": 0.55},
                                    {"name": "draw", "probability": 0.25},
                                    {"name": "away", "probability": 0.20}
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """)
        );

        assertThat(context.teamStrengthDelta()).isNotNull().isPositive();
        assertThat(context.providerProbability()).isNotNull();
        assertThat(context.providerProbability().homeWin()).isEqualTo(0.55);
    }

    private MatchMetadata metadata() {
        return new MatchMetadata(
                "sr:sport_event:test",
                "sr:season:test",
                "sr:competition:test",
                new TeamMetadata("home", "Home", TeamSide.HOME),
                new TeamMetadata("away", "Away", TeamSide.AWAY),
                Instant.parse("2026-04-30T18:00:00Z"),
                0,
                0,
                "not_started",
                "not_started"
        );
    }
}
