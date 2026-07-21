/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Bộ điều hướng xe theo thời gian thực của mảng Spatial/Routing.
 * Đọc cấu hình RoutingRule + trạng thái slot/reservation hiện tại để chọn zone
 * phù hợp cho xe check-in (suggestZone), và dựng bảng trạng thái routing cho
 * màn hình gate console (getRoutingStatus).
 * @Dependencies:
 * - ZoneRepository, SlotRepository, RoutingRuleRepository (Local)
 * - ReservationRepository (Local)
 * - SystemConfigService (Local)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.infrastructure.domain.RoutingRule;
import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.RoutingRuleRepository;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneRoutingService {

    private final ZoneRepository zoneRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;

    /**
     * @Function: getZonePriority
     * @Description: Điểm ưu tiên (càng nhỏ càng ưu tiên) khi sắp xếp zone
     * ứng viên, theo loại khách và chức năng zone.
     * @Logic_Steps:
     * 1. Khách MONTHLY: MONTHLY=1 -> BACKUP=2 -> WALK_IN=3, còn lại=99.
     * 2. Khách vãng lai: WALK_IN=1, còn lại=99.
     */
    private int getZonePriority(String functionType, String customerType) {
        if ("MONTHLY".equalsIgnoreCase(customerType)) {
            if ("MONTHLY".equalsIgnoreCase(functionType)) return 1;
            if ("BACKUP".equalsIgnoreCase(functionType)) return 2;
            if ("WALK_IN".equalsIgnoreCase(functionType)) return 3;
            return 99;
        } else {
            if ("WALK_IN".equalsIgnoreCase(functionType)) return 1;
            return 99;
        }
    }

    /**
     * @Function: getApplicableRules
     * @Description: Chọn đúng 1 rule áp dụng cho mỗi zone tại thời điểm now.
     * @Logic_Steps:
     * 1. Gom activeRules theo zoneId.
     * 2. Mỗi zone: tìm rule khung giờ (không phải default) mà now nằm trong
     *    [startTime, endTime] qua isWithinTimeframe (xử lý cả khung qua đêm).
     * 3. Không có rule khung giờ khớp -> dùng rule isDefault=true của zone.
     */
    private List<RoutingRule> getApplicableRules(List<RoutingRule> activeRules, LocalTime now) {
        List<RoutingRule> applicableRules = new ArrayList<>();
        java.util.Map<Long, List<RoutingRule>> rulesByZone = activeRules.stream()
                .collect(Collectors.groupingBy(r -> r.getZone().getId()));

        for (java.util.Map.Entry<Long, List<RoutingRule>> entry : rulesByZone.entrySet()) {
            List<RoutingRule> zoneRules = entry.getValue();
            RoutingRule timeframeRule = zoneRules.stream()
                    .filter(r -> !r.getIsDefault() && r.getStartTime() != null && r.getEndTime() != null)
                    .filter(r -> isWithinTimeframe(now, r.getStartTime(), r.getEndTime()))
                    .findFirst()
                    .orElse(null);

            if (timeframeRule != null) {
                applicableRules.add(timeframeRule);
            } else {
                zoneRules.stream().filter(rule -> rule.getIsDefault()).findFirst().ifPresent(applicableRules::add);
            }
        }
        return applicableRules;
    }

    /**
     * @Function: isWithinTimeframe
     * @Description: Kiểm tra now có nằm trong [start, end], xử lý đúng khung
     * giờ qua đêm (start > end, VD 22:00-06:00) bằng OR thay vì AND.
     */
    private boolean isWithinTimeframe(LocalTime now, LocalTime start, LocalTime end) {
        if (!start.isAfter(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }

    /**
     * @Function: calculateZoneOccupancy
     * @Description: Tính % lấp đầy realtime của 1 zone, dùng để so ngưỡng
     * trong thuật toán trượt.
     * @Logic_Steps:
     * 1. effectiveCapacity = tổng slot - slot DISABLED; hết chỗ -> 100%.
     * 2. Đếm slot đang OCCUPIED.
     * 3. Đếm reservation PENDING rơi vào cửa sổ đến sớm (config
     *    RESERVATION_EARLY_MINS, mặc định 30 phút).
     * 4. occupancy = (occupied + reservation trong cửa sổ) / effectiveCapacity
     *    * 100 - có thể vượt 100% vì tính cả reservation như nhu cầu.
     */
    public BigDecimal calculateZoneOccupancy(Long zoneId) {
        long totalSlots = slotRepository.countByZoneId(zoneId);
        if (totalSlots == 0) return BigDecimal.ZERO;

        long disabledSlots = slotRepository.countByZoneIdAndStatus(zoneId, "DISABLED");
        long effectiveCapacity = totalSlots - disabledSlots;

        if (effectiveCapacity <= 0) return BigDecimal.valueOf(100);

        long occupiedSlots = slotRepository.countByZoneIdAndStatus(zoneId, "OCCUPIED");

        java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        List<com.pbms.modules.operation.domain.Reservation> pendingList = reservationRepository.findByZoneIdAndStatusIn(zoneId, java.util.Arrays.asList("PENDING"));
        int windowMinutes = 30;
        try {
            windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue());
        } catch (Exception e) {
            log.warn("Could not read RESERVATION_EARLY_MINS config, defaulting to 30");
        }
        final int finalWindowMinutes = windowMinutes;
        long countInWindow = pendingList.stream().filter(r -> {
            java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(finalWindowMinutes);
            java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes());
            return !now.isBefore(startWindow) && !now.isAfter(endWindow);
        }).count();

        long pendingReservations = countInWindow;
        long effectiveLoad = occupiedSlots + pendingReservations;

        return BigDecimal.valueOf(effectiveLoad)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(effectiveCapacity), 2, RoundingMode.HALF_UP);
    }

    /**
     * @Function: isZonePhysicallyFull
     * @Description: Kiểm tra zone đã đầy vật lý chưa (chỉ tính slot thật,
     * không tính reservation - khác calculateZoneOccupancy).
     */
    public boolean isZonePhysicallyFull(Long zoneId) {
        long totalSlots = slotRepository.countByZoneId(zoneId);
        if (totalSlots == 0) return true;

        long disabledSlots = slotRepository.countByZoneIdAndStatus(zoneId, "DISABLED");
        long effectiveCapacity = totalSlots - disabledSlots;

        if (effectiveCapacity <= 0) return true;

        long occupiedSlots = slotRepository.countByZoneIdAndStatus(zoneId, "OCCUPIED");
        return occupiedSlots >= effectiveCapacity;
    }

    /**
     * @Function: suggestZone
     * @Description: Chọn 1 zone tốt nhất cho xe check-in, theo thuật toán
     * "trượt ngưỡng" (sliding threshold).
     * @Logic_Steps:
     * 1. Lọc + sắp xếp zone ứng viên: đúng loại xe, ACTIVE, đúng tầng (nếu
     *    có), không phải IMPOUNDED; khách vãng lai chỉ lấy WALK_IN.
     * 2. Build chuỗi rule áp dụng (getApplicableRules) và xác định các zone
     *    gốc (không bị zone khác trỏ suggestedZone tới).
     * 3. Chọn zone xuất phát qua 4 lớp fallback: gốc đúng loại -> zone đúng
     *    loại bất kỳ -> gốc bất kỳ loại -> zone đầu danh sách.
     * 4. Trượt: zone hiện tại chưa vượt ngưỡng thì chọn luôn; vượt ngưỡng thì
     *    trượt sang suggestedZone kế tiếp (chống lặp vô hạn qua visitedChain).
     * 5. Cả chuỗi đều vượt ngưỡng -> vét: chọn zone đầu tiên còn chỗ vật lý
     *    (< 100%), trước trong visitedChain, sau trong các zone còn lại.
     */
    public Zone suggestZone(VehicleType vehicleType, String customerType, com.pbms.modules.infrastructure.domain.Floor floor) {
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleType.getId()) && "ACTIVE".equals(z.getStatus()))
                .filter(z -> floor == null || (z.getFloor() != null && z.getFloor().getId().equals(floor.getId())))
                .filter(z -> !"IMPOUNDED".equalsIgnoreCase(z.getFunctionType()))
                .filter(z -> {
                    if (!"MONTHLY".equalsIgnoreCase(customerType)) {
                        return "WALK_IN".equalsIgnoreCase(z.getFunctionType());
                    }
                    return true;
                })
                .sorted((z1, z2) -> {
                    int p1 = getZonePriority(z1.getFunctionType(), customerType);
                    int p2 = getZonePriority(z2.getFunctionType(), customerType);
                    if (p1 != p2) return Integer.compare(p1, p2);
                    return z1.getZoneName().compareTo(z2.getZoneName());
                })
                .collect(Collectors.toList());

        if (zones.isEmpty()) {
            log.warn("No active zones found for vehicle type: {}", vehicleType.getTypeName());
            return null;
        }

        LocalTime now = com.pbms.common.utils.TimeProvider.now().toLocalTime();

        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() && r.getZone().getVehicleType().getId().equals(vehicleType.getId()))
                .filter(r -> floor == null || (r.getZone().getFloor() != null && r.getZone().getFloor().getId().equals(floor.getId())))
                .filter(r -> "ACTIVE".equals(r.getZone().getStatus()))
                .collect(Collectors.toList());

        List<RoutingRule> applicableRules = getApplicableRules(activeRules, now);

        Zone currentZone = null;
        List<Zone> roots = new ArrayList<>();
        if (!applicableRules.isEmpty()) {
            for (RoutingRule rule : applicableRules) {
                boolean isSuggestedByOther = applicableRules.stream()
                        .anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
                if (!isSuggestedByOther) {
                    roots.add(rule.getZone());
                }
            }
        }

        String preferredFunctionType = "MONTHLY".equalsIgnoreCase(customerType) ? "MONTHLY" : "WALK_IN";

        if (!roots.isEmpty()) {
            currentZone = roots.stream()
                    .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                    .findFirst()
                    .orElse(null);
        }

        if (currentZone == null) {
            currentZone = zones.stream()
                    .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                    .findFirst()
                    .orElse(null);
        }

        if (currentZone == null && !roots.isEmpty()) {
            currentZone = roots.get(0);
        }

        if (currentZone == null) {
            currentZone = zones.isEmpty() ? null : zones.get(0);
        }

        List<Zone> visitedChain = new ArrayList<>();

        while (currentZone != null) {
            final Long currentZoneId = currentZone.getId();
            if (visitedChain.stream().anyMatch(z -> z.getId().equals(currentZoneId))) {
                log.warn("Infinite routing loop detected at zone: {}", currentZone.getZoneName());
                break;
            }
            visitedChain.add(currentZone);

            BigDecimal occupancy = calculateZoneOccupancy(currentZone.getId());

            final Long czId = currentZone.getId();
            RoutingRule ruleToUse = applicableRules.stream()
                    .filter(r -> r.getZone().getId().equals(czId))
                    .findFirst()
                    .orElse(null);

            if (ruleToUse != null) {
                if (occupancy.compareTo(BigDecimal.valueOf(ruleToUse.getFillThresholdPct())) >= 0) {
                    Zone nextZone = ruleToUse.getSuggestedZone();
                    if (nextZone != null && zones.stream().noneMatch(z -> z.getId().equals(nextZone.getId()))) {
                        log.info("Suggested zone {} is not on the same floor or is inactive. Stopping cascade.", nextZone.getZoneName());
                        break;
                    }
                    log.info("Zone {} exceeded threshold ({} >= {}). Sliding to {}",
                            currentZone.getZoneName(), occupancy, ruleToUse.getFillThresholdPct(),
                            nextZone != null ? nextZone.getZoneName() : "NULL");

                    currentZone = nextZone;
                } else {
                    return currentZone;
                }
            } else {
                if (occupancy.compareTo(BigDecimal.valueOf(100)) < 0) {
                    return currentZone;
                } else {
                    break;
                }
            }
        }

        log.warn("All zones exceeded routing thresholds for vehicle type {}. Falling back to 100% capacity check by priority order.", vehicleType.getTypeName());

        List<Zone> remainingZones = zones.stream()
                .filter(z -> visitedChain.stream().noneMatch(v -> v.getId().equals(z.getId())))
                .collect(Collectors.toList());

        for (Zone z : remainingZones) {
            BigDecimal occ = calculateZoneOccupancy(z.getId());
            RoutingRule r = applicableRules.stream().filter(rule -> rule.getZone().getId().equals(z.getId())).findFirst().orElse(null);
            double threshold = r != null ? r.getFillThresholdPct() : 100.0;
            if (occ.compareTo(BigDecimal.valueOf(threshold)) < 0) {
                return z;
            }
        }

        for (Zone z : zones) {
            BigDecimal occ = calculateZoneOccupancy(z.getId());
            if (occ.compareTo(BigDecimal.valueOf(100)) < 0) {
                return z;
            }
        }

        return null;
    }

    /**
     * @Function: getRoutingStatus
     * @Description: Phiên bản "hiển thị" của suggestZone - trả về toàn bộ
     * danh sách zone kèm số liệu realtime cho màn hình gate console, thay vì
     * chỉ 1 zone được chọn.
     * @Logic_Steps:
     * 1. Lọc + sắp xếp zone ứng viên, build chuỗi rule và chọn zone xuất phát
     *    - giống hệt suggestZone (xem giải thích chi tiết ở đó).
     * 2. Trượt hết chuỗi (không dừng sớm) để gom mọi zone cần hiển thị, ghi
     *    nhớ zone đầu tiên dưới ngưỡng làm actualSuggestedZone.
     * 3. Không tìm được zone dưới ngưỡng -> vét (visitedChain trước, zone còn
     *    lại sau) để chọn actualSuggestedZone.
     * 4. Build 1 dòng ZoneRoutingStatusDTO cho mỗi zone (capacity/occupied/
     *    reserved/available/occupancyRate/fillThresholdPct/isSuggested).
     */
    public List<ZoneRoutingStatusDTO> getRoutingStatus(Long vehicleTypeId, String customerType, Long floorId) {
        List<ZoneRoutingStatusDTO> resultList = new ArrayList<>();

        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleTypeId) && "ACTIVE".equals(z.getStatus()))
                .filter(z -> floorId == null || (z.getFloor() != null && z.getFloor().getId().equals(floorId)))
                .filter(z -> {
                    if (!"MONTHLY".equalsIgnoreCase(customerType)) {
                        return "WALK_IN".equalsIgnoreCase(z.getFunctionType());
                    }
                    return true;
                })
                .sorted((z1, z2) -> {
                    int p1 = getZonePriority(z1.getFunctionType(), customerType);
                    int p2 = getZonePriority(z2.getFunctionType(), customerType);
                    if (p1 != p2) return Integer.compare(p1, p2);
                    return z1.getZoneName().compareTo(z2.getZoneName());
                })
                .collect(Collectors.toList());

        if (zones.isEmpty()) return resultList;

        java.time.LocalTime now = com.pbms.common.utils.TimeProvider.now().toLocalTime();
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() && r.getZone().getVehicleType().getId().equals(vehicleTypeId))
                .filter(r -> floorId == null || (r.getZone().getFloor() != null && r.getZone().getFloor().getId().equals(floorId)))
                .filter(r -> "ACTIVE".equals(r.getZone().getStatus()))
                .collect(Collectors.toList());

        List<RoutingRule> applicableRules = getApplicableRules(activeRules, now);

        Zone currentZone = null;
        List<Zone> roots = new ArrayList<>();
        if (!applicableRules.isEmpty()) {
            for (RoutingRule rule : applicableRules) {
                boolean isSuggestedByOther = applicableRules.stream()
                        .anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
                if (!isSuggestedByOther) {
                    roots.add(rule.getZone());
                }
            }
        }

        String preferredFunctionType = "MONTHLY".equalsIgnoreCase(customerType) ? "MONTHLY" : "WALK_IN";

        if (!roots.isEmpty()) {
            currentZone = roots.stream()
                    .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                    .findFirst()
                    .orElse(null);
        }

        if (currentZone == null) {
            currentZone = zones.stream()
                    .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                    .findFirst()
                    .orElse(null);
        }

        if (currentZone == null && !roots.isEmpty()) {
            currentZone = roots.get(0);
        }

        if (currentZone == null) {
            currentZone = zones.isEmpty() ? null : zones.get(0);
        }

        List<Zone> visitedChain = new ArrayList<>();
        Zone actualSuggestedZone = null;
        boolean suggestionFound = false;

        while (currentZone != null) {
            final Long currentZoneId = currentZone.getId();
            if (visitedChain.stream().anyMatch(z -> z.getId().equals(currentZoneId))) break;
            visitedChain.add(currentZone);

            BigDecimal occupancy = calculateZoneOccupancy(currentZone.getId());
            final Long czId = currentZone.getId();
            RoutingRule ruleToUse = applicableRules.stream().filter(r -> r.getZone().getId().equals(czId)).findFirst().orElse(null);

            if (ruleToUse != null) {
                if (!suggestionFound && occupancy.compareTo(BigDecimal.valueOf(ruleToUse.getFillThresholdPct())) < 0) {
                    actualSuggestedZone = currentZone;
                    suggestionFound = true;
                }
                Zone nextZone = ruleToUse.getSuggestedZone();
                if (nextZone != null && zones.stream().noneMatch(z -> z.getId().equals(nextZone.getId()))) {
                    break;
                }
                currentZone = nextZone;
            } else {
                if (!suggestionFound && occupancy.compareTo(BigDecimal.valueOf(100)) < 0) {
                    actualSuggestedZone = currentZone;
                    suggestionFound = true;
                }
                break;
            }
        }

        if (!suggestionFound) {
            List<Zone> remainingZones = zones.stream()
                    .filter(z -> visitedChain.stream().noneMatch(v -> v.getId().equals(z.getId())))
                    .collect(Collectors.toList());
            for (Zone z : remainingZones) {
                BigDecimal occ = calculateZoneOccupancy(z.getId());
                RoutingRule r = applicableRules.stream().filter(rule -> rule.getZone().getId().equals(z.getId())).findFirst().orElse(null);
                double threshold = r != null ? r.getFillThresholdPct() : 100.0;
                if (occ.compareTo(BigDecimal.valueOf(threshold)) < 0) {
                    actualSuggestedZone = z;
                    suggestionFound = true;
                    break;
                }
            }
        }

        if (!suggestionFound) {
            for (Zone z : zones) {
                if (calculateZoneOccupancy(z.getId()).compareTo(BigDecimal.valueOf(100)) < 0) {
                    actualSuggestedZone = z;
                    suggestionFound = true;
                    break;
                }
            }
        }

        List<Zone> allZonesToDisplay = new ArrayList<>(visitedChain);
        for (Zone z : zones) {
            if (allZonesToDisplay.stream().noneMatch(v -> v.getId().equals(z.getId()))) {
                allZonesToDisplay.add(z);
            }
        }

        for (Zone z : allZonesToDisplay) {
            long totalSlots = slotRepository.countByZoneId(z.getId());
            long disabledSlots = slotRepository.countByZoneIdAndStatus(z.getId(), "DISABLED");
            long effectiveCapacity = totalSlots - disabledSlots;
            long occupiedSlots = slotRepository.countByZoneIdAndStatus(z.getId(), "OCCUPIED");

            int windowMinutes = 30;
            try { windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue()); } catch (Exception e) {}
            java.time.LocalDateTime nowTime = com.pbms.common.utils.TimeProvider.now();
            final int fWin = windowMinutes;
            long countInWindow = reservationRepository.findByZoneIdAndStatusIn(z.getId(), java.util.Arrays.asList("PENDING")).stream().filter(r -> {
                java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(fWin);
                java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes());
                return !nowTime.isBefore(startWindow) && !nowTime.isAfter(endWindow);
            }).count();
            long pendingReservations = countInWindow;

            BigDecimal occRate = calculateZoneOccupancy(z.getId());
            RoutingRule r = applicableRules.stream().filter(rule -> rule.getZone().getId().equals(z.getId())).findFirst().orElse(null);

            resultList.add(ZoneRoutingStatusDTO.builder()
                    .zoneId(z.getId())
                    .zoneName(z.getZoneName())
                    .capacity((int) effectiveCapacity)
                    .occupied((int) occupiedSlots)
                    .reserved((int) pendingReservations)
                    .available((int) Math.max(0, effectiveCapacity - occupiedSlots - pendingReservations))
                    .occupancyRate(occRate.doubleValue())
                    .fillThresholdPct(r != null ? r.getFillThresholdPct() : 100)
                    .isSuggested(actualSuggestedZone != null && actualSuggestedZone.getId().equals(z.getId()))
                    .build());
        }

        return resultList;
    }
}
