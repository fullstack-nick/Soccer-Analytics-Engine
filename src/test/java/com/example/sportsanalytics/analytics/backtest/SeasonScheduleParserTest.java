package com.example.sportsanalytics.analytics.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SeasonScheduleParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeasonScheduleParser parser = new SeasonScheduleParser();

    @Test
    void includesOnlyFinishedSportEvents() throws Exception {
        assertThat(parser.finishedSportEventIds(objectMapper.readTree("""
                {
                  "schedules": [
                    {
                      "sport_event": {"id": "sr:sport_event:1"},
                      "sport_event_status": {"status": "closed", "match_status": "ended"}
                    },
                    {
                      "sport_event": {"id": "sr:sport_event:2"},
                      "sport_event_status": {"status": "not_started", "match_status": "not_started"}
                    },
                    {
                      "sport_event": {"id": "sr:sport_event:3"},
                      "sport_event_status": {"status": "postponed", "match_status": "postponed"}
                    },
                    {
                      "sport_event": {"id": "sr:sport_event:4"},
                      "sport_event_status": {"status": "complete", "match_status": "finished"}
                    }
                  ]
                }
                """))).containsExactly("sr:sport_event:1", "sr:sport_event:4");
    }
}
