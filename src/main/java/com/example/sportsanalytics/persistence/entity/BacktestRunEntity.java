package com.example.sportsanalytics.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "backtest_runs")
@NoArgsConstructor
public class BacktestRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "season_id", nullable = false, length = 80)
    private String seasonId;

    @Column(name = "model_version", nullable = false, length = 80)
    private String modelVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "requested_match_count", nullable = false)
    private int requestedMatchCount;

    @Column(name = "processed_match_count", nullable = false)
    private int processedMatchCount;

    @Column(name = "failed_match_count", nullable = false)
    private int failedMatchCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", columnDefinition = "jsonb")
    private JsonNode metricsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode failureJson;
}
