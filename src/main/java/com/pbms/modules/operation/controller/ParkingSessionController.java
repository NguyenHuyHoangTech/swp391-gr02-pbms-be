package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;

import com.pbms.common.utils.TimeProvider;
import com.pbms.modules.finance.service.PricingCalculatorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.pbms.modules.incident.repository.IncidentTicketRepository;

@RestController
@RequestMapping("/api/v1/parking-sessions")
@RequiredArgsConstructor
public class ParkingSessionController {

    private final ParkingSessionRepository parkingSessionRepository;
    private final PricingCalculatorService pricingCalculatorService;
    private final IncidentTicketRepository incidentTicketRepository;

    /**
     * GET /api/v1/parking-sessions/my-active
     * KhÃ¡ch hÃ ng xem xe Ä‘ang Ä‘á»— qua biá»ƒn sá»‘ (truyá»n qua query param)
     */
    @GetMapping("/my-active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyActiveSession(
            @RequestParam(required = false) String plate,
            @RequestParam(required = false) String rfid) {

        ParkingSession session = null;

        if (plate != null && !plate.isBlank()) {
            java.util.List<ParkingSession> list = parkingSessionRepository.findByPlateOrderByTimeInDesc(plate.trim().toUpperCase());
            for (ParkingSession s : list) {
                if ("ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus())) {
                    session = s;
                    break;
                }
            }
        } else if (rfid != null && !rfid.isBlank()) {
            session = parkingSessionRepository.findByRfidCard_CardCodeAndStatus(rfid.trim(), "ACTIVE").orElse(null);
            if (session == null) {
                session = parkingSessionRepository.findByRfidCard_CardCodeAndStatus(rfid.trim(), "LOCKED").orElse(null);
            }
        }

        if (session == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("found", false);
            empty.put("message", "No active session found for this vehicle");
            return ResponseEntity.ok(ApiResponse.success(empty, "Success"));
        }

        Map<String, Object> result = toSessionMap(session);
        result.put("found", true);
        return ResponseEntity.ok(ApiResponse.success(result, "Session found"));
    }

    /**
     * GET /api/v1/parking-sessions/history?plate=...
     * Lá»‹ch sá»­ Ä‘á»— xe theo biá»ƒn sá»‘
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHistory(
            @RequestParam String plate) {

        List<Map<String, Object>> history = parkingSessionRepository
                .findByPlateOrderByTimeInDesc(plate.trim().toUpperCase())
                .stream()
                .map(this::toSessionMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(history, "Fetched parking history"));
    }

    /**
     * GET /api/v1/parking-sessions/all
     * Láº¥y toÃ n bá»™ lá»‹ch sá»­ Ä‘á»— xe (cÃ³ phÃ¢n trang)
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timeIn"));
        Page<ParkingSession> sessionPage = parkingSessionRepository.findAll(pageRequest);
        
        List<Map<String, Object>> content = sessionPage.getContent().stream()
                .map(this::toSessionMap)
                .collect(Collectors.toList());
                
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalPages", sessionPage.getTotalPages());
        result.put("totalElements", sessionPage.getTotalElements());
        result.put("currentPage", page);
        
        return ResponseEntity.ok(ApiResponse.success(result, "Fetched all sessions"));
    }

    private Map<String, Object> toSessionMap(ParkingSession ps) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ps.getId());
        map.put("plate", ps.getPlate());
        map.put("vehicleType", ps.getVehicleType() != null ? ps.getVehicleType().getTypeName() : null);
        map.put("rfid", ps.getRfidCard() != null ? ps.getRfidCard().getCardCode() : null);
        map.put("timeIn", ps.getTimeIn());
        map.put("timeOut", ps.getTimeOut());
        map.put("gateInName", ps.getGateIn() != null ? ps.getGateIn().getGateName() : null);
        map.put("gateOutName", ps.getGateOut() != null ? ps.getGateOut().getGateName() : null);
        
        BigDecimal currentFee = ps.getTotalFee();
        if (currentFee == null && ps.getVehicleType() != null && ps.getTimeIn() != null && 
            ("ACTIVE".equals(ps.getStatus()) || "LOCKED".equals(ps.getStatus()))) {
            currentFee = pricingCalculatorService.calculateTotalFee(
                    ps.getVehicleType().getId(), 
                    ps.getTimeIn(), 
                    TimeProvider.now());
        }
        
        map.put("totalFee", currentFee);
        map.put("status", ps.getStatus());

        List<com.pbms.modules.incident.domain.IncidentTicket> incidentTickets = incidentTicketRepository.findBySessionId(ps.getId());
        if (incidentTickets != null && !incidentTickets.isEmpty()) {
            List<Map<String, Object>> incidentDetails = incidentTickets.stream()
                .filter(t -> t.getUploadedDocUrl() != null && ("OVERSTAY".equals(t.getIssueType()) || "ZONE_VIOLATION".equals(t.getIssueType())))
                .map(t -> {
                    Map<String, Object> inc = new HashMap<>();
                    inc.put("type", t.getIssueType());
                    inc.put("urls", java.util.Arrays.asList(t.getUploadedDocUrl().split("\\|")));
                    return inc;
                })
                .collect(Collectors.toList());
            if (!incidentDetails.isEmpty()) {
                map.put("incidentDetails", incidentDetails);
            }
        }
        return map;
    }
}

