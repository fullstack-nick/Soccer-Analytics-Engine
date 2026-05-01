package com.example.sportsanalytics.jobs;

import com.example.sportsanalytics.config.SportsAnalyticsProperties;
import com.example.sportsanalytics.application.live.LivePollingService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LivePollingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(LivePollingJob.class);

    private final SportsAnalyticsProperties properties;
    private final LivePollingService pollingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LivePollingJob(SportsAnalyticsProperties properties, LivePollingService pollingService) {
        this.properties = properties;
        this.pollingService = pollingService;
    }

    @Scheduled(fixedDelayString = "${sports.live.poll-delay-ms:10000}")
    public void pollLiveMatches() {
        if (!properties.getLive().isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            pollingService.pollOnce();
        } catch (RuntimeException exception) {
            LOGGER.warn("Live polling tick failed: {}", exception.getMessage());
        } finally {
            running.set(false);
        }
    }
}
