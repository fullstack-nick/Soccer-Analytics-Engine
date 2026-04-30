package com.example.sportsanalytics.application.system;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService implements SystemStatusProvider {
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final Environment environment;
    private final Clock clock;
    private final String applicationName;
    private final String version;

    public SystemStatusService(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            Environment environment,
            @Value("${info.app.name:soccer-intelligence-engine}") String applicationName,
            @Value("${info.app.version:unknown}") String version
    ) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.environment = environment;
        this.applicationName = applicationName;
        this.version = version;
        this.clock = Clock.systemUTC();
    }

    @Override
    public SystemStatusResponse currentStatus() {
        String database = databaseStatus();
        String status = "UP".equals(database) ? "UP" : "DEGRADED";
        return new SystemStatusResponse(
                applicationName,
                version,
                status,
                database,
                activeProfiles(),
                Instant.now(clock)
        );
    }

    private String databaseStatus() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return "UNAVAILABLE";
        }
        try {
            Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
            return Integer.valueOf(1).equals(result) ? "UP" : "DOWN";
        } catch (RuntimeException ex) {
            return "DOWN";
        }
    }

    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return List.of("default");
        }
        return Arrays.asList(profiles);
    }
}
