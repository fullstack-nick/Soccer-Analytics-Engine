package com.example.sportsanalytics.sportradar.client;

public class SportradarNotFoundException extends SportradarClientException {
    public SportradarNotFoundException(SportradarEndpoint endpoint, String providerId) {
        super("Sportradar returned 404 for " + endpoint.sourceEndpoint() + " and provider id " + providerId);
    }
}
