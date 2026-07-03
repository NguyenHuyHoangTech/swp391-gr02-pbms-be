package com.pbms.modules.incident.repository;

import com.pbms.modules.incident.domain.ZoneHourlyTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ZoneHourlyTrendRepository extends JpaRepository<ZoneHourlyTrend, Long> {
    List<ZoneHourlyTrend> findByTimeWindowBetween(LocalDateTime start, LocalDateTime end);
}

