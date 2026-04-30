package com.example.sportsanalytics.persistence.entity;

import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.TeamSide;
import com.example.sportsanalytics.domain.model.TimelineSourceType;
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
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "match_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_match_events_match_provider_event", columnNames = {"match_id", "provider_event_id"})
)
@NoArgsConstructor
public class MatchEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @Column(name = "provider_event_id", length = 80)
    private String providerEventId;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private MatchEventType eventType;

    @Column(name = "occurred_at_minute", nullable = false)
    private int occurredAtMinute;

    @Column(name = "stoppage_time")
    private Integer stoppageTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_side", nullable = false, length = 20)
    private TeamSide teamSide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payload_id")
    private RawPayloadEntity payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "player_ids", nullable = false, columnDefinition = "jsonb")
    private JsonNode playerIds;

    @Column(name = "x")
    private Integer x;

    @Column(name = "y")
    private Integer y;

    @Column(name = "destination_x")
    private Integer destinationX;

    @Column(name = "destination_y")
    private Integer destinationY;

    @Column(name = "xg_value")
    private Double xgValue;

    @Column(name = "outcome", length = 80)
    private String outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_timeline_type", nullable = false, length = 40)
    private TimelineSourceType sourceTimelineType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
