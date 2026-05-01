package com.example.sportsanalytics.api.error;

import com.example.sportsanalytics.application.backtest.BacktestRunNotFoundException;
import com.example.sportsanalytics.application.match.MatchNotFoundException;
import com.example.sportsanalytics.sportradar.client.MissingSportradarApiKeyException;
import com.example.sportsanalytics.sportradar.client.SportradarNotFoundException;
import com.example.sportsanalytics.sportradar.client.SportradarUpstreamException;
import com.example.sportsanalytics.sportradar.mapping.SportradarPayloadMappingException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail validation(ConstraintViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MissingSportradarApiKeyException.class)
    ProblemDetail missingApiKey(MissingSportradarApiKeyException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(MatchNotFoundException.class)
    ProblemDetail matchNotFound(MatchNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(BacktestRunNotFoundException.class)
    ProblemDetail backtestRunNotFound(BacktestRunNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SportradarNotFoundException.class)
    ProblemDetail providerNotFound(SportradarNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({SportradarUpstreamException.class, SportradarPayloadMappingException.class})
    ProblemDetail upstream(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }
}
