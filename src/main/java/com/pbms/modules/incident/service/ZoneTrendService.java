package com.pbms.modules.incident.service;

import com.pbms.modules.incident.domain.ZoneHourlyTrend;
import com.pbms.modules.incident.dto.ZoneTrendDTO;
import com.pbms.modules.incident.repository.ZoneHourlyTrendRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneTrendService {

    private final ZoneHourlyTrendRepository zoneHourlyTrendRepository;
    private final ZoneRepository zoneRepository;

    @Transactional
    public void recordZoneTrend(Long zoneId, BigDecimal occupancyPct) {
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        LocalDateTime window = now.withMinute(0).withSecond(0).withNano(0); // Truncate to hour

        List<ZoneHourlyTrend> trends = zoneHourlyTrendRepository.findByTimeWindowBetween(window, window.plusHours(1).minusNanos(1));
        ZoneHourlyTrend trend = trends.stream().filter(t -> t.getZone().getId().equals(zoneId)).findFirst().orElse(null);

        if (trend == null) {
            trend = ZoneHourlyTrend.builder()
                    .zone(zoneRepository.findById(zoneId).orElse(null))
                    .timeWindow(window)
                    .occupancyPct(occupancyPct)
                    .revenueGenerated(BigDecimal.ZERO)
                    .entriesCount(0)
                    .exitsCount(0)
                    .build();
        } else {
            // Keep the peak occupancy for this hour
            if (occupancyPct.compareTo(trend.getOccupancyPct()) > 0) {
                trend.setOccupancyPct(occupancyPct);
            }
        }
        zoneHourlyTrendRepository.save(trend);
    }

    public List<ZoneTrendDTO> getZoneTrends(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<ZoneHourlyTrend> trends = zoneHourlyTrendRepository.findByTimeWindowBetween(startOfDay, endOfDay);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        return trends.stream()
                .map(t -> ZoneTrendDTO.builder()
                        .timeWindow(t.getTimeWindow().format(formatter))
                        .zoneId(t.getZone().getId())
                        .zoneName(t.getZone().getZoneName())
                        .occupancyPct(t.getOccupancyPct())
                        .build())
                .collect(Collectors.toList());
    }
}

