/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-06
 * @Description: Service for managing map configurations, including floors, zones, slots, and gates.
 * @Dependencies: FloorRepository, ZoneRepository, GateRepository, SlotRepository, VehicleTypeRepository, BuildingProfileRepository, ReservationRepository, SystemConfigService, ParkingSessionRepository
 */
package com.pbms.modules.infrastructure.service;

import com.pbms.modules.infrastructure.domain.Floor;
import com.pbms.modules.infrastructure.domain.Gate;
import com.pbms.modules.infrastructure.domain.Slot;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.infrastructure.dto.config.*;
import com.pbms.modules.infrastructure.repository.FloorRepository;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.system.domain.BuildingProfile;
import com.pbms.modules.system.repository.BuildingProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapConfigurationService {

    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final GateRepository gateRepository;
    private final SlotRepository slotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final BuildingProfileRepository buildingProfileRepository;
    private final ReservationRepository reservationRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final ParkingSessionRepository parkingSessionRepository;

    /**
     * @Function: getMapConfiguration
     * @Description: Retrieves the full configuration of the parking map including floors, zones, gates, and vehicle types.
     * @Logic_Steps:
     * 1. Retrieve all floors, active zones, active gates, and vehicle types from the database.
     * 2. Build FloorConfigDTO objects for each floor.
     * 3. Fetch all active parking sessions to map current vehicle locations and suggested routing.
     * 4. Build ZoneConfigDTO objects, associating active slots, reservations, and suggested vehicles.
     * 5. Build GateConfigDTO and VehicleTypeDTO objects.
     * 6. Return a fully populated MapConfigDTO containing all map data.
     */
    @Transactional(readOnly = true)
    public MapConfigDTO getMapConfiguration() {
        List<Floor> floors = floorRepository.findAll();
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> !"DELETED".equals(z.getStatus()))
                .collect(Collectors.toList());
        List<Gate> gates = gateRepository.findAll().stream()
                .filter(g -> !"DELETED".equals(g.getStatus()))
                .collect(Collectors.toList());

        Map<Long, VehicleType> vehicleTypes = vehicleTypeRepository.findAll().stream()
                .collect(Collectors.toMap(vt -> vt.getId(), Function.identity()));

        List<FloorConfigDTO> floorDTOs = floors.stream().map(f -> FloorConfigDTO.builder()
                .id(f.getId())
                .name(f.getFloorName())
                .type(f.getFloorType())
                .mapCols(f.getMapCols())
                .mapRows(f.getMapRows())
                .build()).collect(Collectors.toList());

        List<com.pbms.modules.operation.domain.ParkingSession> activeSessions = parkingSessionRepository.findByStatusIn(java.util.Arrays.asList("ACTIVE", "LOCKED"));

        Map<Long, String> slotPlateMap = activeSessions.stream()
                .filter(ps -> ps.getSlot() != null)
                .collect(Collectors.toMap(
                    ps -> ps.getSlot().getId(), 
                    ps -> {
                        String plate = ps.getPlate();
                        if (plate != null && !plate.trim().isEmpty()) return plate;
                        if (ps.getRfidCard() != null) return "RFID " + ps.getRfidCard().getCardCode();
                        return "Unknown";
                    }, 
                    (p1, p2) -> p1));

        Map<Long, List<String>> zoneSuggestedVehicles = activeSessions.stream()
                .filter(ps -> ps.getSuggestedZoneId() != null)
                .collect(Collectors.groupingBy(
                    ps -> ps.getSuggestedZoneId(),
                    Collectors.mapping(
                        ps -> {
                            String plate = ps.getPlate();
                            if (plate != null && !plate.trim().isEmpty()) return plate;
                            if (ps.getRfidCard() != null) return "RFID " + ps.getRfidCard().getCardCode();
                            return "Unknown";
                        }, 
                        Collectors.toList()
                    )
                ));

        List<ZoneConfigDTO> zoneDTOs = zones.stream().map(z -> {
            List<SlotConfigDTO> slotDTOs = slotRepository.findByZoneId(z.getId()).stream()
                    .map(s -> SlotConfigDTO.builder()
                            .id(s.getId())
                            .name(s.getSlotName())
                            .status(s.getStatus())
                            .plate(slotPlateMap.get(s.getId()))
                            .build()).collect(Collectors.toList());

            VehicleType vt = vehicleTypes.get(z.getVehicleType().getId());
            
            java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
            List<com.pbms.modules.operation.domain.Reservation> pendingList = reservationRepository.findByZoneIdAndStatus(z.getId(), "PENDING");
            int windowMinutes = 30;
            try {
                windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue());
            } catch (Exception e) {
            }
            final int finalWindowMinutes = windowMinutes;
            long activeReservations = pendingList.stream().filter(r -> {
                java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(finalWindowMinutes);
                java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes());
                return !now.isBefore(startWindow) && !now.isAfter(endWindow);
            }).count();

            List<String> suggested = new ArrayList<>();
            if (zoneSuggestedVehicles.containsKey(z.getId())) {
                suggested.addAll(zoneSuggestedVehicles.get(z.getId()));
            }

            return ZoneConfigDTO.builder()
                    .id(z.getId())
                    .floorId(z.getFloor().getId())
                    .name(z.getZoneName())
                    .capacity(slotDTOs.size())
                    .vehicleTypeId(vt.getId())
                    .vehicleTypeName(vt.getTypeName())
                    .vehicleCategory(vt.getCategory())
                    .functionType(z.getFunctionType())
                    .layoutX(z.getLayoutX())
                    .layoutY(z.getLayoutY())
                    .rotation(z.getRotation())
                    .overflowThreshold(z.getOverflowThreshold())
                    .activeReservationsCount(activeReservations)
                    .suggestedVehicles(suggested)
                    .slots(slotDTOs)
                    .build();
        }).collect(Collectors.toList());

        List<GateConfigDTO> gateDTOs = gates.stream().map(g -> GateConfigDTO.builder()
                .id(g.getId())
                .floorId(g.getFloor().getId())
                .name(g.getGateName())
                .type(g.getGateType())
                .status(g.getStatus())
                .vehicleTypeId(g.getVehicleType() != null ? g.getVehicleType().getId() : null)
                .layoutX(g.getLayoutX())
                .layoutY(g.getLayoutY())
                .rotation(g.getRotation())
                .build()).collect(Collectors.toList());

        List<VehicleTypeDTO> vtDTOs = vehicleTypeRepository.findAll().stream()
                .filter(vt -> "ACTIVE".equals(vt.getStatus() != null ? vt.getStatus() : "ACTIVE"))
                .map(vt -> VehicleTypeDTO.builder()
                        .id(vt.getId())
                        .typeName(vt.getTypeName())
                        .category(vt.getCategory())
                        .matrixWidth(vt.getMatrixWidth())
                        .matrixHeight(vt.getMatrixHeight())
                        .iconUrl(vt.getIconUrl())
                        .build())
                .collect(Collectors.toList());

        return MapConfigDTO.builder()
                .floors(floorDTOs)
                .zones(zoneDTOs)
                .gates(gateDTOs)
                .vehicleTypes(vtDTOs)
                .build();
    }

}
