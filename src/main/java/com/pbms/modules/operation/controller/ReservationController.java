package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.CreateReservationRequest;
import com.pbms.modules.operation.dto.ReservationDTO;
import com.pbms.modules.operation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.pbms.modules.operation.dto.CancelReservationRequest;

@RestController
@RequestMapping("/api/v1/customer/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getAllReservations() {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getAllReservations(), "Fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationDTO>> createReservation(@RequestBody CreateReservationRequest request) {
        try {
            ReservationDTO dto = reservationService.createReservation(request);
            return ResponseEntity.ok(ApiResponse.success(dto, "Reservation created successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> previewReservationPrice(@RequestBody java.util.Map<String, Object> requestBody) {
        try {
            Long vehicleTypeId = Long.valueOf(requestBody.get("vehicleTypeId").toString());
            Integer expectedDurationMinutes = Integer.valueOf(requestBody.get("expectedDurationMinutes").toString());
            java.time.LocalDateTime expectedEntryTime = java.time.LocalDateTime.parse(requestBody.get("expectedEntryTime").toString());
            
            java.math.BigDecimal fee = reservationService.previewPrice(vehicleTypeId, expectedEntryTime, expectedDurationMinutes);
            return ResponseEntity.ok(ApiResponse.success(fee, "Spirituality improves spirituality as a community"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationDTO>> cancelReservation(
            @PathVariable Long id, 
            @RequestBody CancelReservationRequest request) {
        try {
            ReservationDTO dto = reservationService.cancelReservation(id, request);
            return ResponseEntity.ok(ApiResponse.success(dto, "Success"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/plate")
    public ResponseEntity<ApiResponse<ReservationDTO>> updatePlate(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String newPlate = payload.get("plate");
            if (newPlate == null || newPlate.isBlank()) {
                throw new IllegalArgumentException("Plate is required");
            }
            ReservationDTO dto = reservationService.updateReservationPlate(id, newPlate);
            return ResponseEntity.ok(ApiResponse.success(dto, "Plate updated successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Error: " + e.getMessage()));
        }
    }
}

