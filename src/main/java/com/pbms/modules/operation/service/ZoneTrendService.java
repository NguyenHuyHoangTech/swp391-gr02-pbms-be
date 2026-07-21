/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Lưu và truy vấn xu hướng lấp đầy theo giờ của từng zone, phục
 * vụ biểu đồ lịch sử trên dashboard manager. recordZoneTrend ghi/cập nhật
 * đỉnh; getZoneTrends dựng ma trận (ngày x giờ x zone) cho FE vẽ biểu đồ.
 * @Dependencies:
 * - ZoneHourlyTrendRepository (Local, module incident)
 * - ZoneRepository (Local)
 * - ZoneRoutingService (Local)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.incident.domain.ZoneHourlyTrend;
import com.pbms.modules.operation.dto.ZoneTrendDTO;
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
    private final ZoneRoutingService zoneRoutingService;

    /**
     * @Function: recordZoneTrend
     * @Description: Ghi/cập nhật đỉnh occupancy cho 1 (zone, khung giờ tròn).
     * Dùng chung cho job hẹn giờ (ZoneTrendSchedulingService) và mỗi lần slot
     * đổi trạng thái qua IoT.
     * @Logic_Steps:
     * 1. Chưa có bản ghi (zone, window) -> tạo mới.
     * 2. Đã có -> chỉ ghi đè khi giá trị mới cao hơn đỉnh đang lưu.
     * 3. Có nhiều bản trùng (race condition) -> gộp đỉnh cao nhất vào bản
     *    chính, xoá các bản dư.
     */
    @Transactional
    public void recordZoneTrend(Long zoneId, BigDecimal occupancyPct, LocalDateTime timeWindow) {
        LocalDateTime window = timeWindow != null ? timeWindow : com.pbms.common.utils.TimeProvider.now().withMinute(0).withSecond(0).withNano(0);

        List<ZoneHourlyTrend> matchingTrends = zoneHourlyTrendRepository.findByZoneIdAndTimeWindow(zoneId, window);
        ZoneHourlyTrend trend;

        if (matchingTrends.isEmpty()) {
            trend = ZoneHourlyTrend.builder()
                    .zone(zoneRepository.findById(zoneId)
                            .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + zoneId)))
                    .timeWindow(window)
                    .occupancyPct(occupancyPct)
                    .revenueGenerated(BigDecimal.ZERO)
                    .entriesCount(0)
                    .exitsCount(0)
                    .build();
            zoneHourlyTrendRepository.save(trend);
        } else {
            trend = matchingTrends.get(0);
            if (occupancyPct.compareTo(trend.getOccupancyPct()) > 0) {
                trend.setOccupancyPct(occupancyPct);
                zoneHourlyTrendRepository.save(trend);
            }
            if (matchingTrends.size() > 1) {
                for (int i = 1; i < matchingTrends.size(); i++) {
                    ZoneHourlyTrend dup = matchingTrends.get(i);
                    if (dup.getOccupancyPct().compareTo(trend.getOccupancyPct()) > 0) {
                        trend.setOccupancyPct(dup.getOccupancyPct());
                        zoneHourlyTrendRepository.save(trend);
                    }
                    zoneHourlyTrendRepository.delete(dup);
                }
            }
        }
    }

    /**
     * @Function: getZoneTrends
     * @Description: Trả về danh sách phẳng mọi ô (zone x giờ) trong khoảng
     * [startDate, endDate] để FE vẽ biểu đồ, lọc theo loại xe/tầng (null =
     * không lọc).
     * @Logic_Steps:
     * 1. Lấy trend đã lưu trong khoảng ngày, gom vào map tra nhanh theo
     *    "zoneId|timeWindow".
     * 2. Lấy danh sách zone ACTIVE khớp loại xe/tầng.
     * 3. Duyệt từng ngày x từng giờ (0-23), bỏ qua giờ tương lai.
     * 4. Mỗi (zone, giờ): giờ hiện tại -> tính realtime; giờ quá khứ có trend
     *    -> lấy đỉnh đã lưu; giờ quá khứ không có trend -> coi như 0%.
     */
    public List<ZoneTrendDTO> getZoneTrends(LocalDate startDate, LocalDate endDate, Long vehicleTypeId, Long floorId) {
        LocalDateTime startWindow = startDate.atStartOfDay();
        LocalDateTime endWindow = endDate.atTime(LocalTime.MAX);

        List<ZoneHourlyTrend> trends = zoneHourlyTrendRepository.findByTimeWindowBetween(startWindow, endWindow);
        java.util.Map<String, ZoneHourlyTrend> trendMap = trends.stream()
                .collect(Collectors.toMap(
                        t -> t.getZone().getId() + "|" + t.getTimeWindow(),
                        t -> t,
                        (t1, t2) -> t1.getOccupancyPct().compareTo(t2.getOccupancyPct()) >= 0 ? t1 : t2));

        List<com.pbms.modules.infrastructure.domain.Zone> activeZones = zoneRepository.findAll().stream()
                .filter(z -> "ACTIVE".equals(z.getStatus()))
                .filter(z -> vehicleTypeId == null || (z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleTypeId)))
                .filter(z -> floorId == null || (z.getFloor() != null && z.getFloor().getId().equals(floorId)))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00 dd/MM");
        List<ZoneTrendDTO> result = new java.util.ArrayList<>();

        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        LocalDateTime currentHourWindow = now.withMinute(0).withSecond(0).withNano(0);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (int h = 0; h < 24; h++) {
                LocalDateTime window = date.atStartOfDay().plusHours(h);
                if (window.isAfter(currentHourWindow)) {
                    continue;
                }
                String timeStr = window.format(formatter);
                for (com.pbms.modules.infrastructure.domain.Zone z : activeZones) {
                    ZoneHourlyTrend t = trendMap.get(z.getId() + "|" + window);

                    BigDecimal occupancy = null;
                    if (window.equals(currentHourWindow)) {
                        occupancy = zoneRoutingService.calculateZoneOccupancy(z.getId());
                    } else if (t != null) {
                        occupancy = t.getOccupancyPct();
                    } else if (window.isBefore(currentHourWindow)) {
                        occupancy = BigDecimal.ZERO;
                    }

                    result.add(ZoneTrendDTO.builder()
                            .timeWindow(timeStr)
                            .zoneId(z.getId())
                            .zoneName(z.getZoneName())
                            .occupancyPct(occupancy)
                            .build());
                }
            }
        }
        return result;
    }
}
