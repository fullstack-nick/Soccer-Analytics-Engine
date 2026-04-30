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
@Table(name = "raw_payloads")
@NoArgsConstructor
public class RawPayloadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_endpoint", nullable = false, length = 160)
    private String sourceEndpoint;

    @Column(name = "provider_id", length = 80)
    private String providerId;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "cache_control", length = 255)
    private String cacheControl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "provider_package", nullable = false, length = 40)
    private String providerPackage;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode payloadJson;
}
