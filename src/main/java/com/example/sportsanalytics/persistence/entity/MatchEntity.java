package com.example.sportsanalytics.persistence.entity;

import com.example.sportsanalytics.domain.model.CoverageMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "matches")
@NoArgsConstructor
public class MatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_match_id", nullable = false, unique = true, length = 80)
    private String providerMatchId;

    @Column(name = "season_id", nullable = false, length = 80)
    private String seasonId;

    @Column(name = "competition_id", nullable = false, length = 80)
    private String competitionId;

    @Column(name = "home_team_id", nullable = false, length = 80)
    private String homeTeamId;

    @Column(name = "away_team_id", nullable = false, length = 80)
    private String awayTeamId;

    @Column(name = "start_time")
    private Instant startTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_mode", nullable = false, length = 20)
    private CoverageMode coverageMode;
}
