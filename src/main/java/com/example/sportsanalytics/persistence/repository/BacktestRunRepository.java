package com.example.sportsanalytics.persistence.repository;

import com.example.sportsanalytics.persistence.entity.BacktestRunEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestRunRepository extends JpaRepository<BacktestRunEntity, UUID> {
}
