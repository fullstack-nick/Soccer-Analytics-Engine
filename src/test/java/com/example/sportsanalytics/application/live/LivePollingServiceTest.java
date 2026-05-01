package com.example.sportsanalytics.application.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.live.dto.LivePollResult;
import com.example.sportsanalytics.application.match.MatchEventIngestionService;
import com.example.sportsanalytics.application.match.MatchStateRebuildService;
import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.persistence.repository.LiveMatchTrackingRepository;
import com.example.sportsanalytics.sportradar.client.SportradarClient;
import com.example.sportsanalytics.sportradar.mapping.LiveSportradarMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class LivePollingServiceTest {
    @Test
    void pollOnceDoesNothingWhenLivePollingIsDisabled() {
        SportsAnalyticsProperties properties = new SportsAnalyticsProperties();
        SportradarClient sportradarClient = mock(SportradarClient.class);
        LiveMatchTrackingRepository trackingRepository = mock(LiveMatchTrackingRepository.class);
        LivePollingService service = new LivePollingService(
                properties,
                sportradarClient,
                mock(LiveSportradarMapper.class),
                trackingRepository,
                mock(MatchEventIngestionService.class),
                mock(MatchStateRebuildService.class),
                mock(AlertGenerationService.class),
                mock(EntityManager.class),
                Clock.systemUTC()
        );

        LivePollResult result = service.pollOnce();

        assertThat(result.registeredMatches()).isZero();
        verifyNoInteractions(sportradarClient, trackingRepository);
    }
}
