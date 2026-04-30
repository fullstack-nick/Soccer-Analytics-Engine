package com.example.sportsanalytics.persistence.entity;

import com.example.sportsanalytics.domain.model.CoverageMode;
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
@Table(name = "feature_snapshots")
@NoArgsConstructor
public class FeatureSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private MatchEventEntity event;

    @Column(name = "minute", nullable = false)
    private int minute;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode featuresJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_mode", nullable = false, length = 20)
    private CoverageMode coverageMode;

    @Column(name = "feature_set_version", nullable = false, length = 40)
    private String featureSetVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "availability_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode availabilityJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
