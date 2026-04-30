package com.example.sportsanalytics.sportradar.client;

public interface SportradarClient {
    SportradarPayload fetch(SportradarEndpoint endpoint, String providerId, boolean forceRefresh);
}
