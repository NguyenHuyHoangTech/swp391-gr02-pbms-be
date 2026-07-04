/**
 * @Author: Member 2
 * @Date: 2026-07-05
 * @Description: Service managing parking zones.
 * Handles calculation of available slots and retrieval of zone details.
 * @Dependencies: 
 * - ZoneRepository (Local)
 * - SlotRepository (Local)
 * - ReservationRepository (Local)
 * - SystemConfigService (Local)
 */
package com.pbms.modules.infrastructure.service;

import com.pbms.modules.infrastructure.domain.Slot;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.dto.SlotDTO;
import com.pbms.modules.infrastructure.dto.ZoneDTO;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;

import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.system.service.SystemConfigService;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final SystemConfigService systemConfigService;

    /**
     * @Function: getMapZones
     * @Description: Retrieves all zones with their slots and calculates available slots based on pending reservations.
     * @Logic_Steps:
     * 1. Retrieve all zones from ZoneRepository.
     * 2. Loop through each zone:
     *    2.1. Retrieve all slots for the zone.
     *    2.2. Calculate total slots and physically available slots ("EMPTY" or "AVAILABLE").
     *    2.3. Determine reservation window from SystemConfigService (default 30 mins).
     *    2.4. Get current time and calculate pending reservations within the time window.
     *    2.5. Calculate actual available slots (physically available minus pending).
     *    2.6. Map slots to SlotDTOs.
     *    2.7. Build and return ZoneDTO.
     * 3. Return the list of ZoneDTOs.
     * 
     * @returns {List<ZoneDTO>} List of mapped zone DTOs with calculated availability
     */
    public List<ZoneDTO> getMapZones() {
        List<Zone> zones = zoneRepository.findAll();
        
        return zones.stream().map(zone -> {
            List<Slot> slots = slotRepository.findByZoneId(zone.getId());
            long totalSlots = slots.size();
            long physicalAvailableSlots = slots.stream().filter(s -> "EMPTY".equals(s.getStatus()) || "AVAILABLE".equals(s.getStatus())).count();
            
            int windowMinutes = 30;
            try { 
                String configVal = systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue();
                if (configVal != null) windowMinutes = Integer.parseInt(configVal);
            } catch (Exception e) {}
            
            java.time.LocalDateTime nowTime = com.pbms.common.utils.TimeProvider.now();
            final int fWin = windowMinutes;
            long countInWindow = reservationRepository.findByZoneIdAndStatus(zone.getId(), "PENDING").stream().filter(r -> {
                java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(fWin);
                java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes() != null ? r.getExpectedDurationMinutes() : 120);
                return !nowTime.isBefore(startWindow) && !nowTime.isAfter(endWindow);
            }).count();

            long pendingReservations = Math.min(physicalAvailableSlots, countInWindow);
            long availableSlots = physicalAvailableSlots - pendingReservations;

            List<SlotDTO> slotDTOs = slots.stream().map(s -> SlotDTO.builder()
                .id(String.valueOf(s.getId()))
                .name(s.getSlotName())
                .status(s.getStatus())
                .build()
            ).collect(Collectors.toList());

            return ZoneDTO.builder()
                .id(zone.getId())
                .floorId(zone.getFloor().getId())
                .floorName(zone.getFloor().getFloorName())
                .name(zone.getZoneName())
                .capacity((int) totalSlots)
                .availableSlots((int) availableSlots)
                .pendingReservations((int) pendingReservations)
                .vehicleTypeId(zone.getVehicleType().getId())
                .vehicleType(zone.getVehicleType().getTypeName())
                .vehicleMatrixWidth(zone.getVehicleType().getMatrixWidth())
                .vehicleMatrixHeight(zone.getVehicleType().getMatrixHeight())
                .functionType(zone.getFunctionType())
                .layoutX(zone.getLayoutX())
                .layoutY(zone.getLayoutY())
                .rotation(zone.getRotation())
                .slots(slotDTOs)
                .build();
        }).collect(Collectors.toList());
    }
}
