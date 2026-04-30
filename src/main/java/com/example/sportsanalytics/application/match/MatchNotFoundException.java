package com.example.sportsanalytics.application.match;

import java.util.UUID;

public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(UUID matchId) {
        super("Match not found: " + matchId);
    }

    public MatchNotFoundException(String providerMatchId) {
        super("Match not found for provider id: " + providerMatchId);
    }
}
