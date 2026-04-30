package com.example.sportsanalytics.sportradar.client;

public class SportradarUpstreamException extends SportradarClientException {
    private final int statusCode;

    public SportradarUpstreamException(SportradarEndpoint endpoint, String providerId, int statusCode, String message) {
        super("Sportradar call failed for " + endpoint.sourceEndpoint() + " and provider id " + providerId
                + " with HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public SportradarUpstreamException(SportradarEndpoint endpoint, String providerId, Throwable cause) {
        super("Sportradar call failed for " + endpoint.sourceEndpoint() + " and provider id " + providerId, cause);
        this.statusCode = 0;
    }

    public int statusCode() {
        return statusCode;
    }
}
