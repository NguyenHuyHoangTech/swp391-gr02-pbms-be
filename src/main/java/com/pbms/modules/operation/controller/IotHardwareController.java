package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.common.utils.TimeProvider;
import com.pbms.modules.operation.dto.CheckInRequestDTO;
import com.pbms.modules.operation.dto.CheckOutRequestDTO;
import com.pbms.modules.operation.dto.GateResponseDTO;
import com.pbms.modules.operation.dto.SensorEventDto;
import com.pbms.modules.operation.service.GateOperationService;
import com.pbms.modules.operation.service.ZoneMonitoringService;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.infrastructure.repository.FloorRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.pbms.common.event.TimeFastForwardedEvent;
import com.pbms.modules.system.service.SystemConfigService;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import com.pbms.modules.operation.repository.StaffWorkSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/v1/iot")
public class IotHardwareController {

    private final ZoneMonitoringService zoneMonitoringService;
    private final GateOperationService gateOperationService;
    private final SlotRepository slotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;
    private final GateRepository gateRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final RfidCardRepository rfidCardRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final StaffWorkSessionRepository staffWorkSessionRepository;
    private final MonthlyTicketRepository monthlyTicketRepository;
    private final SystemConfigService systemConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public IotHardwareController(ZoneMonitoringService zoneMonitoringService,
                                 GateOperationService gateOperationService,
                                 SlotRepository slotRepository,
                                 ParkingSessionRepository sessionRepository,
                                 ReservationRepository reservationRepository,
                                 GateRepository gateRepository,
                                 VehicleTypeRepository vehicleTypeRepository,
                                 RfidCardRepository rfidCardRepository,
                                 FloorRepository floorRepository,
                                 ZoneRepository zoneRepository,
                                 StaffWorkSessionRepository staffWorkSessionRepository,
                                 MonthlyTicketRepository monthlyTicketRepository,
                                 SystemConfigService systemConfigService,
                                 ApplicationEventPublisher eventPublisher,
                                 SimpMessagingTemplate messagingTemplate) {
        this.zoneMonitoringService = zoneMonitoringService;
        this.gateOperationService = gateOperationService;
        this.slotRepository = slotRepository;
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
        this.gateRepository = gateRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.rfidCardRepository = rfidCardRepository;
        this.floorRepository = floorRepository;
        this.zoneRepository = zoneRepository;
        this.staffWorkSessionRepository = staffWorkSessionRepository;
        this.monthlyTicketRepository = monthlyTicketRepository;
        this.systemConfigService = systemConfigService;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/sensors/update")
    public ResponseEntity<ApiResponse<String>> handleSensorUpdate(@RequestBody SensorEventDto request) {
        zoneMonitoringService.processSensorEvent(request.getSensorId(), request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Processed", "Sensor update processed successfully"));
    }

    @GetMapping("/debug-session")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> debugSession() {
        Map<String, Object> debug = new HashMap<>();
        com.pbms.modules.operation.domain.ParkingSession s = sessionRepository.findById(20L).orElse(null);
        if (s != null) {
            debug.put("plate", s.getPlate());
            debug.put("hasVehicleType", s.getVehicleType() != null);
            if (s.getVehicleType() != null) debug.put("vehicleTypeId", s.getVehicleType().getId());
            debug.put("hasRfidCard", s.getRfidCard() != null);
            if (s.getRfidCard() != null) debug.put("rfidCardId", s.getRfidCard().getId());
            if (s.getRfidCard() != null) debug.put("rfidCardCode", s.getRfidCard().getCardCode());
        }
        return ResponseEntity.ok(debug);
    }

@PostMapping("/time/fast-forward")
    public ResponseEntity<ApiResponse<String>> fastForwardTime(@RequestBody Map<String, String> payload) {
        String targetTimeStr = payload.get("targetTime");
        if (targetTimeStr == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Missing targetTime"));
        }
        LocalDateTime targetTime = LocalDateTime.parse(targetTimeStr);
        try {
            LocalDateTime oldTime = TimeProvider.now();
            TimeProvider.fastForwardTo(targetTime);
            
            // Save offset to DB
            long offsetSeconds = TimeProvider.getSimulatedOffset().getSeconds();
            systemConfigService.saveOrUpdateConfigValue("TIME_SIMULATED_OFFSET_SECONDS", String.valueOf(offsetSeconds));
            
            // Broadcast offset via WebSocket
            Map<String, Object> wsPayload = new HashMap<>();
            wsPayload.put("offsetSeconds", offsetSeconds);
            messagingTemplate.convertAndSend("/topic/time-sync", (Object) wsPayload);

            // Publish Event to trigger cronjobs
            eventPublisher.publishEvent(new TimeFastForwardedEvent(this, oldTime, TimeProvider.now()));
            
            return ResponseEntity.ok(ApiResponse.success("Current Time: " + TimeProvider.now(), "Time fast-forwarded successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/gates/checkin")
    public ResponseEntity<ApiResponse<GateResponseDTO>> simulateCheckIn(@RequestBody CheckInRequestDTO request) {
        GateResponseDTO response = gateOperationService.triggerScanCheckIn(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Check-in triggered"));
    }

    @PostMapping("/gates/checkout")
    public ResponseEntity<ApiResponse<GateResponseDTO>> simulateCheckOut(@RequestBody CheckOutRequestDTO request) {
        GateResponseDTO response = gateOperationService.triggerScanCheckOut(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Check-out triggered"));
    }

    @GetMapping("/data-sync")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncData() {
        Map<String, Object> data = new HashMap<>();
        
        // 1. Current System Time
        data.put("currentTime", TimeProvider.now());
        
        // 1. Slots (Mapped to Map to avoid deep nesting and lazy loading)
        data.put("slots", slotRepository.findAll().stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("slotName", s.getSlotName());
            map.put("status", s.getStatus());
            map.put("currentPlate", s.getCurrentPlate());
            if (s.getZone() != null) {
                map.put("zoneId", s.getZone().getId());
            }
            return map;
        }).toList());
        
        // 3. Active Sessions (Mapped to avoid infinite recursion)
        data.put("activeSessions", sessionRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus()))
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", s.getId());
                    map.put("plate", s.getPlate());
                    map.put("timeIn", s.getTimeIn());
                    map.put("status", s.getStatus());
                    map.put("suggestedZoneName", s.getSuggestedZoneName());
                    map.put("picInPanorama", s.getPicInPanorama());
                    map.put("picInFace", s.getPicInFace());
                    if (s.getGateIn() != null && s.getGateIn().getFloor() != null) {
                        map.put("floorId", s.getGateIn().getFloor().getId());
                    }
                    if (s.getVehicleType() != null) {
                        map.put("vehicleTypeId", s.getVehicleType().getId());
                    }
                    if (s.getRfidCard() != null) {
                        Map<String, Object> rfidMap = new HashMap<>();
                        rfidMap.put("cardCode", s.getRfidCard().getCardCode());
                        map.put("rfidCard", rfidMap);
                    }
                    if (s.getSlot() != null) {
                        Map<String, Object> slotMap = new HashMap<>();
                        slotMap.put("id", s.getSlot().getId());
                        slotMap.put("slotName", s.getSlot().getSlotName());
                        map.put("slot", slotMap);
                    }
                    if (s.getVehicleType() != null) {
                        Map<String, Object> vMap = new HashMap<>();
                        vMap.put("id", s.getVehicleType().getId());
                        vMap.put("typeName", s.getVehicleType().getTypeName());
                        map.put("vehicleType", vMap);
                    }
                    return map;
                })
                .toList());

        // 4. Reservations (Mapped to avoid infinite recursion)
        data.put("reservations", reservationRepository.findAll().stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()) || "PENDING".equals(r.getStatus()))
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", r.getId());
                    map.put("expectedEntryTime", r.getExpectedEntryTime());
                    map.put("reservationFee", r.getReservationFee());
                    map.put("status", r.getStatus());
                    if (r.getVehicle() != null) {
                        Map<String, Object> vMap = new HashMap<>();
                        vMap.put("id", r.getVehicle().getId());
                        vMap.put("plateNumber", r.getVehicle().getPlateNumber());
                        if (r.getVehicle().getVehicleType() != null) {
                            Map<String, Object> vtMap = new HashMap<>();
                            vtMap.put("id", r.getVehicle().getVehicleType().getId());
                            vtMap.put("typeName", r.getVehicle().getVehicleType().getTypeName());
                            vMap.put("vehicleType", vtMap);
                        }
                        map.put("vehicle", vMap);
                    }
                    if (r.getZone() != null) {
                        Map<String, Object> zMap = new HashMap<>();
                        zMap.put("id", r.getZone().getId());
                        zMap.put("zoneName", r.getZone().getZoneName());
                        if (r.getZone().getFloor() != null) {
                            zMap.put("floorId", r.getZone().getFloor().getId());
                        }
                        map.put("zone", zMap);
                    }
                    return map;
                })
                .toList());

        // 5. Gates (Mapped)
        data.put("gates", gateRepository.findAll().stream().map(g -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", g.getId());
            map.put("gateName", g.getGateName());
            map.put("gateType", g.getGateType());
            if (g.getVehicleType() != null) {
                map.put("vehicleTypeId", g.getVehicleType().getId());
            }
            if (g.getFloor() != null) {
                map.put("floorType", g.getFloor().getFloorType());
                map.put("floorId", g.getFloor().getId());
            }
            boolean hasStaff = staffWorkSessionRepository.findByGateIdAndStatus(g.getId(), "ACTIVE").isPresent();
            map.put("hasStaff", hasStaff);
            return map;
        }).toList());

        // 6. Monthly Tickets
        data.put("monthlyTickets", monthlyTicketRepository.findAll().stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", m.getId());
                    map.put("plate", m.getPlate());
                    if (m.getUser() != null) {
                        map.put("customerName", m.getUser().getFullName());
                    }
                    map.put("validFrom", m.getValidFrom());
                    map.put("validUntil", m.getValidUntil());
                    map.put("status", m.getStatus());
                    if (m.getVehicleType() != null) {
                        Map<String, Object> vMap = new HashMap<>();
                        vMap.put("id", m.getVehicleType().getId());
                        vMap.put("typeName", m.getVehicleType().getTypeName());
                        map.put("vehicleType", vMap);
                    }
                    return map;
                }).toList());

        // 7. Vehicle Types (Mapped)
        data.put("vehicleTypes", vehicleTypeRepository.findAll().stream().map(v -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", v.getId());
            map.put("typeName", v.getTypeName());
            map.put("category", v.getCategory());
            map.put("matrixWidth", v.getMatrixWidth());
            map.put("matrixHeight", v.getMatrixHeight());
            return map;
        }).toList());

        // 7. Available RFID Cards
        data.put("availableCards", rfidCardRepository.findByStatus("AVAILABLE").stream()
                .map(c -> c.getCardCode())
                .toList());

        // 8. Floors
        data.put("floors", floorRepository.findAll().stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("floorName", f.getFloorName());
            map.put("floorType", f.getFloorType());
            map.put("mapCols", f.getMapCols());
            map.put("mapRows", f.getMapRows());
            return map;
        }).toList());

        // 9. Zones
        data.put("zones", zoneRepository.findAll().stream().map(z -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", z.getId());
            map.put("zoneName", z.getZoneName());
            if (z.getFloor() != null) {
                map.put("floorId", z.getFloor().getId());
            }
            map.put("layoutX", z.getLayoutX());
            map.put("layoutY", z.getLayoutY());
            map.put("rotation", z.getRotation());
            map.put("functionType", z.getFunctionType());
            if (z.getVehicleType() != null) {
                map.put("vehicleTypeId", z.getVehicleType().getId());
            }
            return map;
        }).toList());

        return ResponseEntity.ok(ApiResponse.success(data, "Synced"));
    }
}

