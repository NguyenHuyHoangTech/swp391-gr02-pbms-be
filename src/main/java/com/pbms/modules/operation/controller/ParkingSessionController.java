/**
 * @Author: Nguyễn Huy Hoàng
 * @Date: 2026-06-28
 * @Description: REST Controller chuyên trách xử lý các nghiệp vụ liên quan đến Phiên đỗ xe (Parking Session).
 * Cung cấp các API để truy vấn phiên đỗ xe hiện tại (my-active), lịch sử đỗ xe, báo cáo thống kê, 
 * và xử lý các thao tác quản lý phiên đỗ xe từ phía Frontend (như khách hàng tự xem thông tin vé, hoặc quản lý tra cứu).
 * 
 * @Dependencies (Các thành phần được tiêm vào nội bộ):
 * - ParkingSessionRepository: Truy xuất và cập nhật dữ liệu cốt lõi của các phiên đỗ xe.
 * - PricingCalculatorService: Tính toán lại mức phí dự kiến (nếu cần xem tạm tính).
 * - IncidentTicketRepository, RefundRequestRepository: Liên kết tra cứu các sự cố hoặc yêu cầu hoàn tiền của một phiên đỗ.
 * - GateOperationService, ZoneRepository, ReservationRepository: Hỗ trợ truy vấn thông tin bổ sung liên quan đến cổng, bãi và đặt chỗ.
 * 
 * @Interactions (Tương tác hệ thống thực tế):
 * - Frontend (React): Được gọi bởi màn hình Web của Khách hàng (để xem thông tin lượt đỗ xe đang diễn ra - my-active) 
 *   hoặc màn hình Quản lý của Quản trị viên (để tra cứu, lọc và xuất báo cáo CSV lịch sử đỗ xe).
 */
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
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Stream;

import com.pbms.common.utils.TimeProvider;
import com.pbms.modules.finance.service.PricingCalculatorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.pbms.modules.incident.repository.IncidentTicketRepository;

@RestController
@RequestMapping("/api/v1/operation/parking-sessions")
@RequiredArgsConstructor
public class ParkingSessionController {

    private final ParkingSessionRepository parkingSessionRepository;
    private final PricingCalculatorService pricingCalculatorService;
    private final IncidentTicketRepository incidentTicketRepository;
    private final com.pbms.modules.infrastructure.repository.ZoneRepository zoneRepository;
    private final com.pbms.modules.operation.service.GateOperationService gateOperationService;
    private final com.pbms.modules.operation.repository.ReservationRepository reservationRepository;
    private final com.pbms.modules.finance.repository.RefundRequestRepository refundRequestRepository;

    /**
     * GET /api/v1/operation/parking-sessions/my-active
     * Get active parking session by plate or RFID
     */
    @GetMapping("/my-active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyActiveSession(
            @RequestParam(required = false) String plate,
            @RequestParam(required = false) String rfid,
            @RequestParam(required = false) Long vehicleTypeId) {

        ParkingSession session = null;

        if (plate != null && !plate.isBlank() && rfid != null && !rfid.isBlank()) {
            java.util.List<ParkingSession> list = parkingSessionRepository.findByPlateContainingIgnoreCaseOrderByTimeInDesc(plate.trim());
            for (ParkingSession s : list) {
                if (("ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus())) 
                        && s.getRfidCard() != null 
                        && (s.getRfidCard().getCardCode().toUpperCase().endsWith(rfid.trim().toUpperCase()) 
                            || (s.getRfidCard().getCardId() != null && s.getRfidCard().getCardId().toUpperCase().endsWith(rfid.trim().toUpperCase())))) {
                    if (vehicleTypeId != null && s.getVehicleType() != null && !s.getVehicleType().getId().equals(vehicleTypeId)) {
                        continue;
                    }
                    session = s;
                    break;
                }
            }
        } else if (plate != null && !plate.isBlank()) {
            java.util.List<ParkingSession> list = parkingSessionRepository.findByPlateContainingIgnoreCaseOrderByTimeInDesc(plate.trim());
            for (ParkingSession s : list) {
                if ("ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus())) {
                    if (vehicleTypeId != null && s.getVehicleType() != null && !s.getVehicleType().getId().equals(vehicleTypeId)) {
                        continue;
                    }
                    session = s;
                    break;
                }
            }
        } else if (rfid != null && !rfid.isBlank()) {
            session = parkingSessionRepository.findByRfidCard_CardCodeAndStatus(rfid.trim(), "ACTIVE")
                    .orElseGet(() -> parkingSessionRepository.findByRfidCard_CardIdAndStatus(rfid.trim(), "ACTIVE").orElse(null));
            if (session == null) {
                session = parkingSessionRepository.findByRfidCard_CardCodeAndStatus(rfid.trim(), "LOCKED")
                        .orElseGet(() -> parkingSessionRepository.findByRfidCard_CardIdAndStatus(rfid.trim(), "LOCKED").orElse(null));
            }
            if (session != null && vehicleTypeId != null && session.getVehicleType() != null && !session.getVehicleType().getId().equals(vehicleTypeId)) {
                session = null;
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
     * Get combined history: parking sessions + reservations by license plate
     */
    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHistory(
            @RequestParam String plate) {

        String normalizedPlate = plate.trim().toUpperCase();

        // 1. Actual parking sessions (the vehicle physically entered)
        List<Map<String, Object>> sessionHistory = parkingSessionRepository
                .findByPlateOrderByTimeInDesc(normalizedPlate)
                .stream()
                .map(this::toSessionMap)
                .collect(Collectors.toList());

        // 2. Reservation history (bookings, cancellations, no-shows, forfeited fees)
        List<Map<String, Object>> reservationHistory = reservationRepository
                .findByVehicle_PlateNumberOrderByExpectedEntryTimeDesc(normalizedPlate)
                .stream()
                .filter(r -> !"".equals(r.getStatus())) // include all statuses
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("recordType", "RESERVATION");
                    m.put("id", r.getId());
                    m.put("plate", normalizedPlate);
                    m.put("status", r.getStatus());
                    m.put("zoneName", r.getZone() != null ? r.getZone().getZoneName() : "N/A");
                    m.put("expectedEntryTime", r.getExpectedEntryTime());
                    m.put("expectedDurationMinutes", r.getExpectedDurationMinutes());
                    m.put("reservationFee", r.getReservationFee());
                    java.util.Optional<com.pbms.modules.finance.domain.RefundRequest> refundReq = 
                            refundRequestRepository.findByReferenceTypeAndReferenceId("RESERVATION", String.valueOf(r.getId()));
                    java.math.BigDecimal refundAmount = refundReq.map(req -> req.getRefundAmount()).orElse(null);
                    String refundStatus = refundReq.map(req -> req.getStatus()).orElse(null);
                    String refundProofUrl = refundReq.map(req -> req.getProofUrl()).orElse(null);
                    
                    m.put("refundAmount", refundAmount);
                    m.put("refundStatus", refundStatus);
                    m.put("refundProofUrl", refundProofUrl);
                    // forfeited = fee paid - refund
                    java.math.BigDecimal fee = r.getReservationFee() != null ? r.getReservationFee() : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal refund = refundAmount != null ? refundAmount : java.math.BigDecimal.ZERO;
                    m.put("forfeitedAmount", fee.subtract(refund));
                    return m;
                })
                .collect(Collectors.toList());

        // Merge and sort by time descending (sessions by timeIn, reservations by expectedEntryTime)
        List<Map<String, Object>> combined = new java.util.ArrayList<>();
        combined.addAll(sessionHistory);
        combined.addAll(reservationHistory);
        combined.sort((a, b) -> {
            java.time.LocalDateTime ta = a.get("timeIn") != null
                    ? (java.time.LocalDateTime) a.get("timeIn")
                    : (java.time.LocalDateTime) a.get("expectedEntryTime");
            java.time.LocalDateTime tb = b.get("timeIn") != null
                    ? (java.time.LocalDateTime) b.get("timeIn")
                    : (java.time.LocalDateTime) b.get("expectedEntryTime");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        return ResponseEntity.ok(ApiResponse.success(combined, "Fetched combined parking history"));
    }

    /**
     * GET /api/v1/parking-sessions/all
     * Get all parking sessions with pagination
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timeIn"));
        Page<ParkingSession> sessionPage;
        
        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                sessionPage = parkingSessionRepository.findByTimeInBetweenAndStatus(start, end, status.toUpperCase(), pageRequest);
            } else {
                sessionPage = parkingSessionRepository.findByTimeInBetween(start, end, pageRequest);
            }
        } else {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                sessionPage = parkingSessionRepository.findByStatus(status.toUpperCase(), pageRequest);
            } else {
                sessionPage = parkingSessionRepository.findAll(pageRequest);
            }
        }
        
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

    /**
     * GET /api/v1/operation/parking-sessions/export
     * Stream CSV export of parking sessions
     */
    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> exportTable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
            
        StreamingResponseBody stream = out -> {
            try (PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8)) {
                writer.write('\ufeff'); // BOM for Excel
                writer.println("ID,License Plate,Vehicle Type,RFID,Entry Time,Entry Gate,Exit Time,Exit Gate,Total Fee,Overtime Fee,Penalty Fee,Status");
                
                LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.of(2000, 1, 1).atStartOfDay();
                LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : TimeProvider.now().plusDays(1);
                
                try (Stream<ParkingSession> sessionStream = parkingSessionRepository.readByTimeInBetweenOrderByTimeInDesc(start, end)) {
                    sessionStream.forEach(ps -> {
                        String timeIn = ps.getTimeIn() != null ? ps.getTimeIn().toString() : "";
                        String timeOut = ps.getTimeOut() != null ? ps.getTimeOut().toString() : "";
                        String vType = ps.getVehicleType() != null ? ps.getVehicleType().getTypeName() : "";
                        String rfid = ps.getRfidCard() != null ? ps.getRfidCard().getCardCode() : "";
                        String gateIn = ps.getGateIn() != null ? ps.getGateIn().getGateName() : "";
                        String gateOut = ps.getGateOut() != null ? ps.getGateOut().getGateName() : "";
                        String fee = ps.getParkingFee() != null ? ps.getParkingFee().toString() : "0";
                        String overtimeFee = ps.getOvertimeFee() != null ? ps.getOvertimeFee().toString() : "0";
                        String penaltyFee = ps.getPenaltyFee() != null ? ps.getPenaltyFee().toString() : "0";
                        
                        writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                ps.getId(),
                                escapeCsv(ps.getPlate()),
                                escapeCsv(vType),
                                escapeCsv(rfid),
                                timeIn,
                                escapeCsv(gateIn),
                                timeOut,
                                escapeCsv(gateOut),
                                fee,
                                overtimeFee,
                                penaltyFee,
                                ps.getStatus()
                        );
                    });
                }
                writer.flush();
            }
        };

        String filename = "history_export.csv";
        if (startDate != null && endDate != null) {
            filename = "history_export_" + startDate + "_to_" + endDate + ".csv";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(stream);
    }

    private String escapeCsv(String data) {
        if (data == null) return "";
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            escapedData = "\"" + escapedData.replace("\"", "\"\"") + "\"";
        }
        return escapedData;
    }


    private Map<String, Object> toSessionMap(ParkingSession ps) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ps.getId());
        map.put("plate", ps.getPlate());
        map.put("vehicleType", ps.getVehicleType() != null ? ps.getVehicleType().getTypeName() : null);
        map.put("rfid", ps.getRfidCard() != null ? ps.getRfidCard().getCardCode() : null);
        map.put("cardId", ps.getRfidCard() != null ? ps.getRfidCard().getCardId() : null);
        map.put("timeIn", ps.getTimeIn());
        map.put("timeOut", ps.getTimeOut());
        map.put("gateInName", ps.getGateIn() != null ? ps.getGateIn().getGateName() : null);
        map.put("gateOutName", ps.getGateOut() != null ? ps.getGateOut().getGateName() : null);

        String suggestedZoneName = "N/A";
        Long suggestedZoneId = ps.getSuggestedZoneId();
        
        if (ps.getSuggestedZoneId() != null) {
            if (ps.getSuggestedZoneId() == -1L) {
                suggestedZoneName = "FREE";
            } else {
                suggestedZoneName = zoneRepository.findById(ps.getSuggestedZoneId()).map(z -> z.getZoneName()).orElse("N/A");
            }
            suggestedZoneId = ps.getSuggestedZoneId();
        } else if (ps.getReservation() != null && ps.getReservation().getZone() != null) {
            suggestedZoneName = ps.getReservation().getZone().getZoneName();
            suggestedZoneId = ps.getReservation().getZone().getId();
        }
        map.put("suggestedZoneName", suggestedZoneName);
        map.put("suggestedZoneId", suggestedZoneId);
        
        BigDecimal currentFee = ps.getParkingFee();
        BigDecimal currentBaseFee = ps.getParkingFee() != null ? ps.getParkingFee() : BigDecimal.ZERO;
        BigDecimal currentOvertimeFee = ps.getOvertimeFee() != null ? ps.getOvertimeFee() : BigDecimal.ZERO;
        
        if (currentFee != null && ps.getOvertimeFee() != null) {
            currentFee = currentFee.add(ps.getOvertimeFee());
        }
        
        com.pbms.modules.operation.dto.CheckOutSessionInfoDTO checkoutInfo = null;
        if (currentFee == null && ps.getVehicleType() != null && ps.getTimeIn() != null && 
            ("ACTIVE".equals(ps.getStatus()) || "LOCKED".equals(ps.getStatus()))) {
            try {
                String rfidCode = ps.getRfidCard() != null ? ps.getRfidCard().getCardCode() : null;
                checkoutInfo = gateOperationService.getCheckOutSessionInfo(rfidCode, ps.getPlate());
                if (checkoutInfo != null && (checkoutInfo.getExpectedFee() != null || checkoutInfo.getOvertimeFee() != null)) {
                    currentBaseFee = checkoutInfo.getExpectedFee() != null ? checkoutInfo.getExpectedFee() : BigDecimal.ZERO;
                    currentOvertimeFee = checkoutInfo.getOvertimeFee() != null ? checkoutInfo.getOvertimeFee() : BigDecimal.ZERO;
                    currentFee = currentBaseFee.add(currentOvertimeFee);
                } else {
                    currentBaseFee = pricingCalculatorService.calculateParkingFee(
                        ps.getVehicleType().getId(), 
                        ps.getTimeIn(), 
                        TimeProvider.now());
                    currentFee = currentBaseFee;
                }
            } catch (Exception e) {
                currentBaseFee = pricingCalculatorService.calculateParkingFee(
                    ps.getVehicleType().getId(), 
                    ps.getTimeIn(), 
                    TimeProvider.now());
                currentFee = currentBaseFee;
            }
        }

        List<com.pbms.modules.incident.domain.IncidentTicket> incidentTickets = incidentTicketRepository.findBySessionId(ps.getId());
        BigDecimal totalPenalty = BigDecimal.ZERO;
        if (incidentTickets != null && !incidentTickets.isEmpty()) {
            totalPenalty = incidentTickets.stream()
                .map(t -> t.getFineAmount())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        }

        if (currentFee != null) {
            currentFee = currentFee.add(totalPenalty);
        }
        
        map.put("totalFee", currentFee);
        map.put("baseFee", currentBaseFee);
        map.put("overtimeFee", currentOvertimeFee);
        map.put("status", ps.getStatus());
        map.put("checkoutInfo", checkoutInfo);

        if (incidentTickets != null && !incidentTickets.isEmpty()) {
            List<Map<String, Object>> incidentDetails = incidentTickets.stream()
                .map(t -> {
                    Map<String, Object> inc = new HashMap<>();
                    inc.put("type", t.getIssueType());
                    if (t.getUploadedDocUrl() != null) {
                        inc.put("urls", java.util.Arrays.asList(t.getUploadedDocUrl().split("\\|")));
                    }
                    inc.put("fineAmount", t.getFineAmount());
                    inc.put("status", t.getStatus());
                    inc.put("description", t.getDescription());
                    return inc;
                })
                .collect(Collectors.toList());
            if (!incidentDetails.isEmpty()) {
                map.put("incidentDetails", incidentDetails);
            }
        }
        return map;
    }
    @GetMapping("/active/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchActiveSessions(
            @RequestParam String plate,
            @RequestParam Long vehicleTypeId) {
        List<ParkingSession> sessions = parkingSessionRepository.findByPlateAndVehicleTypeIdAndStatus(plate.trim().toUpperCase(), vehicleTypeId, "ACTIVE");
        
        List<Map<String, Object>> result = sessions.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("plate", s.getPlate());
            map.put("vehicleType", s.getVehicleType() != null ? s.getVehicleType().getTypeName() : "UNKNOWN");
            map.put("timeIn", s.getTimeIn());
            map.put("picInPanorama", s.getPicInPanorama());
            map.put("picInFace", s.getPicInFace());
            map.put("rfid", s.getRfidCard() != null ? s.getRfidCard().getCardCode() : "N/A");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result, "Found " + result.size() + " active sessions"));
    }
}

