package com.example.sportsanalytics.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "probability_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProbabilitySnapshotEntity {
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

    @Column(name = "home_win", nullable = false)
    private double homeWin;

    @Column(name = "draw", nullable = false)
    private double draw;

    @Column(name = "away_win", nullable = false)
    private double awayWin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanations_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode explanationsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
