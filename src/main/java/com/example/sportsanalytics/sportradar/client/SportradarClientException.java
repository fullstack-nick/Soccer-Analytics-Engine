package com.example.sportsanalytics.sportradar.client;

public class SportradarClientException extends RuntimeException {
    public SportradarClientException(String message) {
        super(message);
    }

    public SportradarClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
