package com.pbms.modules.operation.service;

import com.pbms.modules.infrastructure.domain.RoutingRule;
import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.RoutingRuleRepository;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
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
    private final ParkingSessionRepository parkingSessionRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;

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
     * Calculates the real-time occupancy percentage of a zone.
     * Formula: (Occupied + Pending Reservations) / (Total - Disabled/Maintenance) * 100
     */
    public BigDecimal calculateZoneOccupancy(Long zoneId) {
        long totalSlots = slotRepository.countByZoneId(zoneId);
        if (totalSlots == 0) return BigDecimal.ZERO;

        long disabledSlots = slotRepository.countByZoneIdAndStatus(zoneId, "DISABLED");
        long effectiveCapacity = totalSlots - disabledSlots;
        
        if (effectiveCapacity <= 0) return BigDecimal.valueOf(100); // Fully disabled

        long occupiedSlots = slotRepository.countByZoneIdAndStatus(zoneId, "OCCUPIED");
        // Only count pending reservations that are active right now
        // Window: [expectedEntryTime - window_minutes, expectedEntryTime + expectedDurationMinutes]
        java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        List<com.pbms.modules.operation.domain.Reservation> pendingList = reservationRepository.findByZoneIdAndStatus(zoneId, "PENDING");
        int windowMinutes = 30;
        try {
            windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES").getConfigValue());
        } catch (Exception e) {
            log.warn("Could not read RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES config, defaulting to 30");
        }
        final int finalWindowMinutes = windowMinutes;
        long pendingReservations = pendingList.stream().filter(r -> {
            java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(finalWindowMinutes);
            java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes());
            return !now.isBefore(startWindow) && !now.isAfter(endWindow);
        }).count();
        long effectiveLoad = occupiedSlots + pendingReservations;

        return BigDecimal.valueOf(effectiveLoad)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(effectiveCapacity), 2, RoundingMode.HALF_UP);
    }

    /**
     * Suggests the best zone for a given vehicle type based on the sliding threshold algorithm.
     */
    public Zone suggestZone(VehicleType vehicleType, String customerType) {
        // 1. Get all active zones for this vehicle type, sorted alphabetically
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleType.getId()) && "ACTIVE".equals(z.getStatus()))
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

        LocalTime now = LocalTime.now();

        // Find ALL active rules for this vehicle type
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() && r.getZone().getVehicleType().getId().equals(vehicleType.getId()))
                .collect(Collectors.toList());

        // Determine the applicable timeframe rules
        List<RoutingRule> applicableRules = new ArrayList<>();
        
        for (RoutingRule r : activeRules) {
            if (!r.getIsDefault() && r.getStartTime() != null && r.getEndTime() != null) {
                if (!now.isBefore(r.getStartTime()) && !now.isAfter(r.getEndTime())) {
                    applicableRules.add(r);
                }
            }
        }

        if (applicableRules.isEmpty()) {
            applicableRules = activeRules.stream()
                    .filter(RoutingRule::getIsDefault)
                    .collect(Collectors.toList());
        }

        // 2. Determine starting zone based on customerType & function_type
        Zone currentZone = null;
        String preferredFunctionType = "MONTHLY".equalsIgnoreCase(customerType) ? "MONTHLY" : "WALK_IN";
        
        currentZone = zones.stream()
                .filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType()))
                .findFirst()
                .orElse(null);

        // Fallback if no matching function_type is found
        if (currentZone == null) {
            if (!applicableRules.isEmpty()) {
                for (RoutingRule rule : applicableRules) {
                    boolean isSuggestedByOther = applicableRules.stream()
                            .anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
                    if (!isSuggestedByOther) {
                        currentZone = rule.getZone();
                        break;
                    }
                }
                if (currentZone == null) currentZone = applicableRules.get(0).getZone();
            } else {
                currentZone = zones.get(0);
            }
        }
        
        List<Zone> visitedChain = new ArrayList<>();
        
        while (currentZone != null) {
            // Prevent infinite loop (e.g. A -> B -> A)
            if (visitedChain.contains(currentZone)) {
                log.warn("Infinite routing loop detected at zone: {}", currentZone.getZoneName());
                break;
            }
            visitedChain.add(currentZone);

            BigDecimal occupancy = calculateZoneOccupancy(currentZone.getId());
            
            // Find the specific rule for THIS zone within the applicable rules
            final Long czId = currentZone.getId();
            RoutingRule ruleToUse = applicableRules.stream()
                    .filter(r -> r.getZone().getId().equals(czId))
                    .findFirst()
                    .orElse(null);
            
            if (ruleToUse != null) {
                if (occupancy.compareTo(BigDecimal.valueOf(ruleToUse.getFillThresholdPct())) >= 0) {
                    // Threshold exceeded, slide to the next zone
                    log.info("Zone {} exceeded threshold ({} >= {}). Sliding to {}", 
                            currentZone.getZoneName(), occupancy, ruleToUse.getFillThresholdPct(), 
                            ruleToUse.getSuggestedZone() != null ? ruleToUse.getSuggestedZone().getZoneName() : "NULL");
                    
                    currentZone = ruleToUse.getSuggestedZone();
                } else {
                    // Below threshold, we can use this zone
                    return currentZone;
                }
            } else {
                // No routing rule for this zone. 
                // We just check if it's full (100%). If not, use it.
                if (occupancy.compareTo(BigDecimal.valueOf(100)) < 0) {
                    return currentZone;
                } else {
                    // It's 100% full, and no rule to cascade. Stop chain.
                    break;
                }
            }
        }

        // 3. Fallback Mechanism (Vét/100%): All zones in the chain are either full or exceeded thresholds.
        // We iterate again over the routing chain (priority order) and return the first one that has ANY available slots (< 100%).
        log.warn("All zones exceeded routing thresholds for vehicle type {}. Falling back to 100% capacity check by priority order.", vehicleType.getTypeName());
        
        List<Zone> remainingZones = zones.stream()
            .filter(z -> !visitedChain.contains(z))
            .collect(Collectors.toList());

        for (Zone z : visitedChain) {
            BigDecimal occ = calculateZoneOccupancy(z.getId());
            if (occ.compareTo(BigDecimal.valueOf(100)) < 0) {
                return z;
            }
        }

        for (Zone z : remainingZones) {
            BigDecimal occ = calculateZoneOccupancy(z.getId());
            if (occ.compareTo(BigDecimal.valueOf(100)) < 0) {
                return z;
            }
        }

        // Absolutely full
        return null;
    }

    /**
     * Calculates the full routing chain and real-time statistics for the check-in screen.
     */
    public List<ZoneRoutingStatusDTO> getRoutingStatus(Long vehicleTypeId, String customerType) {
        List<ZoneRoutingStatusDTO> resultList = new ArrayList<>();
        
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> z.getVehicleType() != null && z.getVehicleType().getId().equals(vehicleTypeId) && "ACTIVE".equals(z.getStatus()))
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

        if (zones.isEmpty()) return resultList;

        java.time.LocalTime now = java.time.LocalTime.now();
        List<RoutingRule> activeRules = routingRuleRepository.findAll().stream()
                .filter(r -> r.getIsActive() && r.getZone().getVehicleType().getId().equals(vehicleTypeId))
                .collect(Collectors.toList());

        List<RoutingRule> applicableRules = new ArrayList<>();
        for (RoutingRule r : activeRules) {
            if (!r.getIsDefault() && r.getStartTime() != null && r.getEndTime() != null) {
                if (!now.isBefore(r.getStartTime()) && !now.isAfter(r.getEndTime())) {
                    applicableRules.add(r);
                }
            }
        }
        if (applicableRules.isEmpty()) {
            applicableRules = activeRules.stream().filter(RoutingRule::getIsDefault).collect(Collectors.toList());
        }

        Zone currentZone = null;
        String preferredFunctionType = "MONTHLY".equalsIgnoreCase(customerType) ? "MONTHLY" : "WALK_IN";
        currentZone = zones.stream().filter(z -> preferredFunctionType.equalsIgnoreCase(z.getFunctionType())).findFirst().orElse(null);

        if (currentZone == null) {
            if (!applicableRules.isEmpty()) {
                for (RoutingRule rule : applicableRules) {
                    boolean isSuggestedByOther = applicableRules.stream().anyMatch(r -> r.getSuggestedZone() != null && r.getSuggestedZone().getId().equals(rule.getZone().getId()));
                    if (!isSuggestedByOther) {
                        currentZone = rule.getZone();
                        break;
                    }
                }
                if (currentZone == null) currentZone = applicableRules.get(0).getZone();
            } else {
                currentZone = zones.get(0);
            }
        }
        
        List<Zone> visitedChain = new ArrayList<>();
        Zone actualSuggestedZone = null;
        boolean suggestionFound = false;

        while (currentZone != null) {
            if (visitedChain.contains(currentZone)) break;
            visitedChain.add(currentZone);

            BigDecimal occupancy = calculateZoneOccupancy(currentZone.getId());
            final Long czId = currentZone.getId();
            RoutingRule ruleToUse = applicableRules.stream().filter(r -> r.getZone().getId().equals(czId)).findFirst().orElse(null);
            
            if (ruleToUse != null) {
                if (!suggestionFound && occupancy.compareTo(BigDecimal.valueOf(ruleToUse.getFillThresholdPct())) < 0) {
                    actualSuggestedZone = currentZone;
                    suggestionFound = true;
                }
                currentZone = ruleToUse.getSuggestedZone();
            } else {
                if (!suggestionFound && occupancy.compareTo(BigDecimal.valueOf(100)) < 0) {
                    actualSuggestedZone = currentZone;
                    suggestionFound = true;
                }
                break; // No rule to cascade
            }
        }

        if (!suggestionFound) {
            for (Zone z : visitedChain) {
                if (calculateZoneOccupancy(z.getId()).compareTo(BigDecimal.valueOf(100)) < 0) {
                    actualSuggestedZone = z;
                    suggestionFound = true;
                    break;
                }
            }
            if (!suggestionFound) {
                List<Zone> remainingZones = zones.stream()
                        .filter(z -> !visitedChain.contains(z))
                        .collect(Collectors.toList());
                for (Zone z : remainingZones) {
                    if (calculateZoneOccupancy(z.getId()).compareTo(BigDecimal.valueOf(100)) < 0) {
                        actualSuggestedZone = z;
                        break;
                    }
                }
            }
        }

        List<Zone> allZonesToDisplay = new ArrayList<>(visitedChain);
        for (Zone z : zones) {
            if (!allZonesToDisplay.contains(z)) allZonesToDisplay.add(z);
        }

        for (Zone z : allZonesToDisplay) {
            long totalSlots = slotRepository.countByZoneId(z.getId());
            long disabledSlots = slotRepository.countByZoneIdAndStatus(z.getId(), "DISABLED");
            long effectiveCapacity = totalSlots - disabledSlots;
            long occupiedSlots = slotRepository.countByZoneIdAndStatus(z.getId(), "OCCUPIED");
            
            int windowMinutes = 30;
            try { windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES").getConfigValue()); } catch (Exception e) {}
            java.time.LocalDateTime nowTime = com.pbms.common.utils.TimeProvider.now();
            final int fWin = windowMinutes;
            long pendingReservations = reservationRepository.findByZoneIdAndStatus(z.getId(), "PENDING").stream().filter(r -> {
                java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(fWin);
                java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes());
                return !nowTime.isBefore(startWindow) && !nowTime.isAfter(endWindow);
            }).count();

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
