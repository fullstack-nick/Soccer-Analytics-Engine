package com.example.sportsanalytics.persistence.entity;

import com.example.sportsanalytics.domain.model.LiveTrackingStatus;
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

@Getter
@Setter
@Entity
@Table(name = "live_match_tracking")
@NoArgsConstructor
public class LiveMatchTrackingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @Column(name = "provider_match_id", nullable = false, length = 80)
    private String providerMatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_status", nullable = false, length = 40)
    private LiveTrackingStatus trackingStatus;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "last_poll_at")
    private Instant lastPollAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_delta_payload_id")
    private RawPayloadEntity lastDeltaPayload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_full_timeline_payload_id")
    private RawPayloadEntity lastFullTimelinePayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
