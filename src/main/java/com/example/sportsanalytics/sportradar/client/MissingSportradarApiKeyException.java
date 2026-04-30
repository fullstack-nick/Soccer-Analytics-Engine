package com.example.sportsanalytics.sportradar.client;

public class MissingSportradarApiKeyException extends SportradarClientException {
    public MissingSportradarApiKeyException() {
        super("SPORTRADAR_API_KEY is required before calling Sportradar");
    }
}
