/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-22
 * @Description: Bộ hẹn giờ chốt xu hướng lấp đầy zone theo từng giờ. Mỗi đầu
 * giờ, lấy đỉnh occupancy của giờ vừa qua (từ ZoneOccupancyTracker) và lưu
 * vào DB (qua ZoneTrendService). Có 2 nguồn kích hoạt: @Scheduled (đồng hồ
 * thật) và handleTimeFastForward (khi đồng hồ mô phỏng bị tua nhanh).
 * @Dependencies:
 * - ZoneRepository (Local)
 * - ZoneOccupancyTracker, ZoneRoutingService, ZoneTrendService (Local)
 */
package com.pbms.modules.operation.service;

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
    private final ZoneTrendService zoneTrendService;

    /**
     * @Function: recordHourlyZoneTrends
     * @Description: Job chạy tự động mỗi đầu giờ đồng hồ thật. Vì chạy đúng
     * lúc HH:00, giờ cần chốt là giờ VỪA KẾT THÚC (HH-1):00 nên phải lùi lại
     * 1 giờ trước khi gọi recordTrendForTime().
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recordHourlyZoneTrends() {
        LocalDateTime timeWindow = com.pbms.common.utils.TimeProvider.now().minusHours(1).withMinute(0).withSecond(0)
                .withNano(0);
        recordTrendForTime(timeWindow);
    }

    /**
     * @Function: handleTimeFastForward
     * @Description: Khi thời gian mô phỏng (TimeProvider) nhảy từ oldTime ->
     * newTime, @Scheduled thật không tự bắn kịp (vì nó chạy theo đồng hồ
     * thật) - nên phải tự "chốt bù" từng giờ đã bị bỏ lỡ trong khoảng nhảy qua.
     */
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime oldTime = event.getOldSimulatedTime().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime newTime = event.getNewSimulatedTime().withMinute(0).withSecond(0).withNano(0);

        log.info("Handling TimeFastForwardedEvent: Catching up trends from {} to {}", oldTime, newTime);

        if (oldTime.isBefore(newTime)) {
            LocalDateTime iter = oldTime;
            while (iter.isBefore(newTime)) {
                recordTrendForTime(iter);
                iter = iter.plusHours(1);
            }
        }
    }

    /**
     * @Function: recordTrendForTime
     * @Description: Chốt trend cho 1 khung giờ cụ thể: với mỗi zone ACTIVE,
     * lấy đỉnh occupancy đã ghi nhận trong giờ đó (đồng thời reset mốc theo
     * dõi cho giờ tiếp theo), rồi lưu thành 1 bản ghi trend.
     */
    private void recordTrendForTime(LocalDateTime timeWindow) {
        log.info("Recording hourly zone trend for time window: {}", timeWindow);
        List<Zone> zones = zoneRepository.findAll();

        for (Zone zone : zones) {
            if (!"ACTIVE".equals(zone.getStatus()))
                continue;

            BigDecimal currentOccupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
            BigDecimal peakOccupancy = zoneOccupancyTracker.getAndResetPeakOccupancy(zone.getId(), currentOccupancy);

            zoneTrendService.recordZoneTrend(zone.getId(), peakOccupancy, timeWindow);
            log.debug("Recorded trend for Zone {}: Peak = {}%", zone.getZoneName(), peakOccupancy);
        }
    }
}
