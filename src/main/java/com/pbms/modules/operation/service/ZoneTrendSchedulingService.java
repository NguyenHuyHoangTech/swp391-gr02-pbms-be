package com.pbms.modules.operation.service;

import com.pbms.modules.incident.domain.ZoneHourlyTrend;
import com.pbms.modules.incident.repository.ZoneHourlyTrendRepository;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneTrendSchedulingService {

    private final ZoneRepository zoneRepository;
    private final ZoneOccupancyTracker zoneOccupancyTracker;
    private final ZoneRoutingService zoneRoutingService;
    private final ZoneHourlyTrendRepository zoneHourlyTrendRepository;

    /**
     * Runs every hour at minute 0 (e.g. 10:00, 11:00)
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recordHourlyZoneTrends() {
        LocalDateTime timeWindow = com.pbms.common.utils.TimeProvider.now().withMinute(0).withSecond(0).withNano(0);
        recordTrendForTime(timeWindow);
    }

    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime oldTime = event.getOldSimulatedTime().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime newTime = event.getNewSimulatedTime().withMinute(0).withSecond(0).withNano(0);

        log.info("Handling TimeFastForwardedEvent: Catching up trends from {} to {}", oldTime, newTime);

        if (oldTime.isBefore(newTime)) {
            LocalDateTime iter = oldTime.plusHours(1);
            while (!iter.isAfter(newTime)) {
                recordTrendForTime(iter);
                iter = iter.plusHours(1);
            }
        } else {
            recordTrendForTime(newTime);
        }
    }

    private void recordTrendForTime(LocalDateTime timeWindow) {
        log.info("Recording hourly zone trend for time window: {}", timeWindow);
        List<Zone> zones = zoneRepository.findAll();
        
        for (Zone zone : zones) {
            if (!"ACTIVE".equals(zone.getStatus())) continue;
            
            // 1. Get current real-time occupancy
            BigDecimal currentOccupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
            
            // 2. Get the peak (high-water mark) for the past hour and reset it
            BigDecimal peakOccupancy = zoneOccupancyTracker.getAndResetPeakOccupancy(zone.getId(), currentOccupancy);
            
            // 3. Save to database
            ZoneHourlyTrend trend = ZoneHourlyTrend.builder()
                    .zone(zone)
                    .timeWindow(timeWindow)
                    .occupancyPct(peakOccupancy)
                    // Currently we don't track revenue/entries per hour yet, set default to 0
                    .revenueGenerated(BigDecimal.ZERO)
                    .entriesCount(0)
                    .exitsCount(0)
                    .build();
                    
            zoneHourlyTrendRepository.save(trend);
            log.debug("Recorded trend for Zone {}: Peak = {}%", zone.getZoneName(), peakOccupancy);
        }
    }
}

