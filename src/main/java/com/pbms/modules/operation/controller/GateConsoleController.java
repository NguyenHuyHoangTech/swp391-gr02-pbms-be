package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import com.pbms.modules.operation.dto.CheckInRequestDTO;
import com.pbms.modules.operation.dto.CheckOutRequestDTO;
import com.pbms.modules.operation.dto.GateResponseDTO;
import com.pbms.modules.operation.service.GateOperationService;
import com.pbms.modules.operation.service.ZoneRoutingService;
import com.pbms.modules.operation.service.ReservationService;
import com.pbms.modules.operation.service.ReservationConflictScheduler;
import com.pbms.modules.operation.dto.ReservationDTO;
import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operation/gates")
@RequiredArgsConstructor
public class GateConsoleController {

    private final GateOperationService gateOperationService;
    private final ZoneRoutingService zoneRoutingService;
    private final ReservationService reservationService;
    private final ReservationConflictScheduler reservationConflictScheduler;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<GateResponseDTO>> processCheckIn(@RequestBody CheckInRequestDTO request) {
        try {
            GateResponseDTO response = gateOperationService.processCheckIn(request);
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
            }
            return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<GateResponseDTO>> processCheckOut(@RequestBody CheckOutRequestDTO request) {
        try {
            GateResponseDTO response = gateOperationService.processCheckOut(request);
            if ("ERROR".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, response.getMessage()));
            }
            if ("WARNING".equals(response.getStatus())) {
                return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
            }
            return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/checkout-session-info")
    public ResponseEntity<ApiResponse<com.pbms.modules.operation.dto.CheckOutSessionInfoDTO>> getCheckOutSessionInfo(
            @RequestParam(required = false) String rfid,
            @RequestParam(required = false) String plate) {
        try {
            com.pbms.modules.operation.dto.CheckOutSessionInfoDTO info = gateOperationService.getCheckOutSessionInfo(rfid, plate);
            return ResponseEntity.ok(ApiResponse.success(info, "Fetched session info successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/routing-status")
    public ResponseEntity<ApiResponse<List<ZoneRoutingStatusDTO>>> getRoutingStatus(
            @RequestParam Long vehicleTypeId,
            @RequestParam(defaultValue = "WALK_IN") String customerType) {
        try {
            List<ZoneRoutingStatusDTO> statusList = zoneRoutingService.getRoutingStatus(vehicleTypeId, customerType);
            return ResponseEntity.ok(ApiResponse.success(statusList, "Fetched routing status successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
    @PostMapping("/reservations/{id}/resolve-zone")
    public ResponseEntity<ApiResponse<ReservationDTO>> resolveReservationZone(
            @PathVariable Long id,
            @RequestParam Long newZoneId) {
        try {
            ReservationDTO dto = reservationService.resolveZone(id, newZoneId, reservationConflictScheduler);
            return ResponseEntity.ok(ApiResponse.success(dto, "Reservation zone resolved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
