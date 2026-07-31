/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: JPA Repository for the ZoneHourlyTrend entity. Provides the window
 *               range lookup used to build the hourly occupancy trend chart.
 * @Dependencies:
 * - ZoneHourlyTrend (com.pbms.modules.operation.domain.ZoneHourlyTrend)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.ZoneHourlyTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ZoneHourlyTrendRepository extends JpaRepository<ZoneHourlyTrend, Long> {
    List<ZoneHourlyTrend> findByTimeWindowBetween(LocalDateTime start, LocalDateTime end);
}
