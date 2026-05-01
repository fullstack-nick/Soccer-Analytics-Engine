package com.example.sportsanalytics.persistence.entity;

import com.example.sportsanalytics.domain.model.AlertSeverity;
import com.example.sportsanalytics.domain.model.AlertType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "match_alerts")
@NoArgsConstructor
public class MatchAlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private MatchEventEntity event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "probability_snapshot_id")
    private ProbabilitySnapshotEntity probabilitySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 60)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(name = "minute", nullable = false)
    private int minute;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode detailsJson;

    @Column(name = "deduplication_key", nullable = false, length = 180)
    private String deduplicationKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
