/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-06
 * @Description: Service for managing map configurations, including floors, zones, slots, and gates.
 * @Dependencies: FloorRepository, ZoneRepository, GateRepository, SlotRepository, VehicleTypeRepository, BuildingProfileRepository, ReservationRepository, SystemConfigService
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

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * @Function: getMapConfiguration
     * @Description: Retrieves the full configuration of the parking map including floors, zones, gates, and vehicle types.
     * @Logic_Steps:
     * 1. Retrieve all floors, active zones, active gates, and vehicle types from the database.
     * 2. Build FloorConfigDTO objects for each floor.
     * 3. Build ZoneConfigDTO objects, associating active slots and pending reservations.
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

        List<ZoneConfigDTO> zoneDTOs = zones.stream().map(z -> {
            List<SlotConfigDTO> slotDTOs = slotRepository.findByZoneId(z.getId()).stream()
                    .map(s -> SlotConfigDTO.builder()
                            .id(s.getId())
                            .name(s.getSlotName())
                            .status(s.getStatus())
                            .build()).collect(Collectors.toList());

            VehicleType vt = z.getVehicleType() != null ? vehicleTypes.get(z.getVehicleType().getId()) : null;
            
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

            return ZoneConfigDTO.builder()
                    .id(z.getId())
                    .floorId(z.getFloor() != null ? z.getFloor().getId() : null)
                    .name(z.getZoneName())
                    .capacity(slotDTOs.size())
                    .vehicleTypeId(vt != null ? vt.getId() : null)
                    .vehicleTypeName(vt != null ? vt.getTypeName() : null)
                    .vehicleCategory(vt != null ? vt.getCategory() : null)
                    .functionType(z.getFunctionType())
                    .layoutX(z.getLayoutX())
                    .layoutY(z.getLayoutY())
                    .rotation(z.getRotation())
                    .overflowThreshold(z.getOverflowThreshold())
                    .activeReservationsCount(activeReservations)
                    .slots(slotDTOs)
                    .build();
        }).collect(Collectors.toList());

        List<GateConfigDTO> gateDTOs = gates.stream().map(g -> GateConfigDTO.builder()
                .id(g.getId())
                .floorId(g.getFloor() != null ? g.getFloor().getId() : null)
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

    /**
     * @Function: saveMapConfiguration
     * @Description: Persists changes made to the map configuration from the frontend editor.
     * @Logic_Steps:
     * 1. Retrieve the default building profile.
     * 2. Process and save incoming floor configurations. Handle new floors and updates to existing floors. Ensure active zones prevent floor type modifications.
     * 3. Process and save incoming zone configurations. Apply soft-delete constraints for zones with missing IDs or zero capacity.
     * 4. Ensure soft-deleted zones do not contain occupied slots to prevent data inconsistencies.
     * 5. Process and update slots belonging to zones.
     * 6. Process and save incoming gate configurations.
     */
    @Transactional
    public void saveMapConfiguration(MapConfigDTO mapConfig) {
        BuildingProfile defaultBuilding = buildingProfileRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No building profile configured"));

        // Ghi lại giá trị CŨ (cấu hình map trước khi ghi đè) vào AuditContext -
        // bỏ slots (đặt về mảng rỗng) trước khi serialize vì danh sách slot
        // quá lớn/không cần thiết cho mục đích audit.
        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null) {
                MapConfigDTO oldConfig = getMapConfiguration();
                if (oldConfig.getZones() != null) {
                    oldConfig.getZones().forEach(z -> z.setSlots(new ArrayList<>()));
                }
                context.setOldValue(objectMapper.writeValueAsString(oldConfig));
            }
        } catch (Exception e) {
        }

        List<Floor> currentFloors = floorRepository.findAll();
        Map<Long, Floor> floorMap = currentFloors.stream().collect(Collectors.toMap(f -> f.getId(), Function.identity()));

        for (FloorConfigDTO fDTO : mapConfig.getFloors()) {
            Floor floor;
            if (fDTO.getId() > 1000000000L || !floorMap.containsKey(fDTO.getId())) {
                floor = Floor.builder()
                        .building(defaultBuilding)
                        .floorName(fDTO.getName())
                        .floorLevel(1) 
                        .capacity(0)
                        .floorType(fDTO.getType())
                        .mapCols(fDTO.getMapCols())
                        .mapRows(fDTO.getMapRows())
                        .build();
                floor = floorRepository.save(floor);
                Long oldId = fDTO.getId();
                fDTO.setId(floor.getId());
                final Long newFloorId = floor.getId();
                mapConfig.getZones().stream().filter(z -> z.getFloorId().equals(oldId)).forEach(z -> z.setFloorId(newFloorId));
                mapConfig.getGates().stream().filter(g -> g.getFloorId().equals(oldId)).forEach(g -> g.setFloorId(newFloorId));
            } else {
                floor = floorMap.get(fDTO.getId());
                
                final Long currentFloorId = floor.getId();
                if (!floor.getFloorType().equals(fDTO.getType())) {
                    boolean hasZones = zoneRepository.findAll().stream()
                            .anyMatch(z -> z.getFloor().getId().equals(currentFloorId) && !"DELETED".equals(z.getStatus()));
                    if (hasZones) {
                        throw new RuntimeException("Cannot change floor type because it has active zones: " + floor.getFloorName());
                    }
                }
                
                floor.setFloorName(fDTO.getName());
                floor.setFloorType(fDTO.getType());
                floor.setMapCols(fDTO.getMapCols());
                floor.setMapRows(fDTO.getMapRows());
                floorRepository.save(floor);
            }
        }

        List<Zone> currentZones = zoneRepository.findAll();
        Map<Long, Zone> zoneMap = currentZones.stream().collect(Collectors.toMap(z -> z.getId(), Function.identity()));
        
        List<Long> incomingZoneIds = mapConfig.getZones().stream()
                .filter(z -> z.getId() < 1000000000L)
                .map(z -> z.getId()).collect(Collectors.toList());
        for (Zone cz : currentZones) {
            if (!incomingZoneIds.contains(cz.getId()) && !"DELETED".equals(cz.getStatus())) {
                List<Slot> cSlots = slotRepository.findByZoneId(cz.getId());
                if (cSlots.stream().anyMatch(s -> "OCCUPIED".equals(s.getStatus()))) {
                    throw new RuntimeException("Cannot delete zone because it has occupied slots: " + cz.getZoneName());
                }
                cz.setStatus("DELETED");
                zoneRepository.save(cz);
                for (Slot s : cSlots) {
                    slotRepository.delete(s);
                }
            }
        }

        for (ZoneConfigDTO zDTO : mapConfig.getZones()) {
            if (zDTO.getCapacity() == 0) {
                if (zDTO.getId() < 1000000000L && zoneMap.containsKey(zDTO.getId())) {
                    Zone cz = zoneMap.get(zDTO.getId());
                    List<Slot> cSlots = slotRepository.findByZoneId(cz.getId());
                    if (cSlots.stream().anyMatch(s -> "OCCUPIED".equals(s.getStatus()))) {
                        throw new RuntimeException("Cannot delete zone because it has occupied slots: " + cz.getZoneName());
                    }
                    cz.setStatus("DELETED");
                    zoneRepository.save(cz);
                    for (Slot s : cSlots) {
                        slotRepository.delete(s);
                    }
                }
                continue;
            }

            Floor f = floorRepository.findById(zDTO.getFloorId()).orElseThrow();
            VehicleType vt = vehicleTypeRepository.findById(zDTO.getVehicleTypeId()).orElseThrow();

            if (!f.getFloorType().equals(vt.getCategory())) {
                throw new RuntimeException("Zone vehicle type does not match floor type for zone: " + zDTO.getName());
            }

            Zone zone;
            if (zDTO.getId() > 1000000000L || !zoneMap.containsKey(zDTO.getId())) {
                zone = Zone.builder()
                        .floor(f)
                        .zoneName(zDTO.getName())
                        .vehicleType(vt)
                        .functionType(zDTO.getFunctionType())
                        .layoutX(zDTO.getLayoutX())
                        .layoutY(zDTO.getLayoutY())
                        .rotation(zDTO.getRotation())
                        .overflowThreshold(zDTO.getOverflowThreshold())
                        .status("ACTIVE")
                        .build();
                zone = zoneRepository.save(zone);
                
                zDTO.setId(zone.getId()); 
            } else {
                zone = zoneMap.get(zDTO.getId());
                zone.setZoneName(zDTO.getName());
                zone.setVehicleType(vt);
                zone.setFunctionType(zDTO.getFunctionType());
                zone.setLayoutX(zDTO.getLayoutX());
                zone.setLayoutY(zDTO.getLayoutY());
                zone.setRotation(zDTO.getRotation());
                zone.setOverflowThreshold(zDTO.getOverflowThreshold());
                zone.setStatus("ACTIVE");
                zone = zoneRepository.save(zone);
            }

            List<Slot> existingSlots = slotRepository.findByZoneId(zone.getId());
            Map<Long, Slot> existingSlotMap = existingSlots.stream().collect(Collectors.toMap(s -> s.getId(), Function.identity()));
            List<Long> incomingSlotIds = zDTO.getSlots().stream().map(s -> s.getId()).collect(Collectors.toList());

            for (Slot es : existingSlots) {
                if (!incomingSlotIds.contains(es.getId())) {
                    if ("OCCUPIED".equals(es.getStatus())) {
                        throw new RuntimeException("Cannot delete occupied slot: " + es.getSlotName());
                    }
                    slotRepository.delete(es);
                }
            }

            for (SlotConfigDTO sDTO : zDTO.getSlots()) {
                if (sDTO.getId() != null && existingSlotMap.containsKey(sDTO.getId())) {
                    Slot es = existingSlotMap.get(sDTO.getId());
                    es.setSlotName(sDTO.getName());
                    if ("DISABLED".equals(sDTO.getStatus()) && "OCCUPIED".equals(es.getStatus())) {
                        throw new RuntimeException("Cannot disable an occupied slot: " + es.getSlotName());
                    }
                    if (!"OCCUPIED".equals(es.getStatus())) {
                        es.setStatus(sDTO.getStatus());
                    }
                    slotRepository.save(es);
                } else {
                    Slot newSlot = Slot.builder()
                            .zone(zone)
                            .slotName(sDTO.getName())
                            .status(sDTO.getStatus())
                            .build();
                    slotRepository.save(newSlot);
                }
            }
        }

        List<Gate> currentGates = gateRepository.findAll();
        Map<Long, Gate> gateMap = currentGates.stream().collect(Collectors.toMap(g -> g.getId(), Function.identity()));

        List<Long> incomingGateIds = mapConfig.getGates().stream()
                .filter(g -> g.getId() != null && g.getId() < 1000000000L)
                .map(g -> g.getId()).collect(Collectors.toList());

        for (Gate cg : currentGates) {
            if (!incomingGateIds.contains(cg.getId()) && !"DELETED".equals(cg.getStatus())) {
                if ("OCCUPIED".equals(cg.getStatus())) {
                    throw new IllegalStateException(
                        "Cannot delete gate \"" + cg.getGateName() + "\" because a staff member is currently on duty at this gate.");
                }
                cg.setStatus("DELETED");
                gateRepository.save(cg);
            }
        }

        for (GateConfigDTO gDTO : mapConfig.getGates()) {
            if (gDTO.getStatus() != null && gDTO.getStatus().equals("DELETED")) {
                if (gDTO.getId() != null && gateMap.containsKey(gDTO.getId())) {
                    Gate gToDelete = gateMap.get(gDTO.getId());
                    if ("OCCUPIED".equals(gToDelete.getStatus())) {
                        throw new IllegalStateException(
                            "Cannot delete gate \"" + gToDelete.getGateName() + "\" because a staff member is currently on duty at this gate.");
                    }
                    if (!"DELETED".equals(gToDelete.getStatus())) {
                        gToDelete.setStatus("DELETED");
                        gateRepository.save(gToDelete);
                    }
                }
                continue;
            }

            Floor f = null;
            if (gDTO.getFloorId() != null) {
                f = floorRepository.findById(gDTO.getFloorId()).orElse(null);
            }

            VehicleType gvt = null;
            if (gDTO.getVehicleTypeId() != null) {
                gvt = vehicleTypeRepository.findById(gDTO.getVehicleTypeId()).orElse(null);
            }
            
            Gate gate;
            if (gDTO.getId() == null || gDTO.getId() > 1000000000L || !gateMap.containsKey(gDTO.getId())) {
                gate = Gate.builder()
                        .floor(f)
                        .vehicleType(gvt)
                        .gateName(gDTO.getName())
                        .gateType(gDTO.getType())
                        .status(gDTO.getStatus() != null ? gDTO.getStatus() : "IDLE")
                        .liveOverrideMode("NORMAL")
                        .layoutX(gDTO.getLayoutX())
                        .layoutY(gDTO.getLayoutY())
                        .rotation(gDTO.getRotation())
                        .build();
                gate = gateRepository.save(gate);
                gDTO.setId(gate.getId());
            } else {
                gate = gateMap.get(gDTO.getId());
                gate.setGateName(gDTO.getName());
                gate.setFloor(f);
                gate.setVehicleType(gvt);
                gate.setGateType(gDTO.getType());
                gate.setLayoutX(gDTO.getLayoutX());
                gate.setLayoutY(gDTO.getLayoutY());
                gate.setRotation(gDTO.getRotation());
                if (!"OCCUPIED".equals(gate.getStatus())) {
                    gate.setStatus(gDTO.getStatus() != null ? gDTO.getStatus() : "IDLE");
                }
                gateRepository.save(gate);
            }
        }

        // Ghi lại giá trị MỚI (cấu hình vừa lưu xong) vào AuditContext - cùng
        // quy tắc bỏ slots như khối "old value" ở đầu hàm.
        try {
            com.pbms.common.context.AuditContext context = com.pbms.common.context.AuditContextHolder.getContext();
            if (context != null) {
                if (mapConfig.getZones() != null) {
                    mapConfig.getZones().forEach(z -> z.setSlots(new ArrayList<>()));
                }
                context.setNewValue(objectMapper.writeValueAsString(mapConfig));
            }
        } catch (Exception e) {
        }
    }
}
