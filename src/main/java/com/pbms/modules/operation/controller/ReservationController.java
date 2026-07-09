/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: REST Controller for managing Prebooking Reservations by Customers.
 *               Handles fetching, creating, previewing prices, cancelling, and updating plate numbers.
 * @Dependencies: 
 * - ReservationService (Local)
 */
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

    /**
     * @Function: getAllReservations
     * @Description: Fetches all reservations (Admin view).
     * @returns ResponseEntity<ApiResponse<List<ReservationDTO>>> - List of reservations
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getAllReservations() {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getAllReservations(), "Fetched successfully"));
    }

    /**
     * @Function: createReservation
     * @Description: Endpoint to create a new PENDING reservation.
     * @param request - CreateReservationRequest containing vehicle, zone and time details
     * @returns ResponseEntity<ApiResponse<ReservationDTO>> - the created reservation
     */
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

    /**
     * @Function: previewReservationPrice
     * @Description: Calculates the estimated price for a reservation before creating it.
     * @param requestBody - JSON object containing vehicleTypeId, expectedDurationMinutes, expectedEntryTime
     * @returns ResponseEntity<ApiResponse<BigDecimal>> - The estimated fee
     */
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

    /**
     * @Function: cancelReservation
     * @Description: Cancels a PENDING reservation and processes the refund based on time constraints.
     * @param id - Reservation ID
     * @param request - CancelReservationRequest containing banking info for the refund
     * @returns ResponseEntity<ApiResponse<ReservationDTO>> - Updated reservation
     */
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

    /**
     * @Function: updatePlate
     * @Description: Updates the plate number of a PENDING reservation before the customer arrives.
     * @param id - Reservation ID
     * @param payload - JSON map containing the new "plate" string
     * @returns ResponseEntity<ApiResponse<ReservationDTO>> - Updated reservation
     */
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

    /**
     * @Function: resolveConflict
     * @Description: Manually resolves a zone capacity conflict by a staff member.
     * @param id - Reservation ID
     * @returns ResponseEntity<ApiResponse<String>> - Success message
     */
    @PostMapping("/{id}/resolve-conflict")
    public ResponseEntity<ApiResponse<String>> resolveConflict(@PathVariable Long id) {
        try {
            reservationService.attemptResolveConflict(id);
            return ResponseEntity.ok(ApiResponse.success("Virtual slot reserved successfully", "Success"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Error: " + e.getMessage()));
        }
    }
}

