/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: REST Controller exposing the Pre-Booking API for customers and staff.
 *               Delegates all business logic to ReservationService and Scheduler.
 * @Dependencies:
 *  - ReservationService           (Local)
 *  - ReservationConflictScheduler (Local)
 *  - ApiResponse                  (com.pbms.common.dto)
 */
package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.CancelReservationRequest;
import com.pbms.modules.operation.dto.CreateReservationRequest;
import com.pbms.modules.operation.dto.ReservationDTO;
import com.pbms.modules.operation.service.ReservationConflictScheduler;
import com.pbms.modules.operation.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customer/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationConflictScheduler reservationConflictScheduler;

    /**
     * @Endpoint: GET /api/v1/customer/reservations
     * @Description: Lấy danh sách lịch sử đặt chỗ (tự động filter theo quyền của user đang login)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getAllReservations() {
        List<ReservationDTO> data = reservationService.getAllReservations();
        return ResponseEntity.ok(ApiResponse.success(data, "Fetched reservations successfully"));
    }

    /**
     * @Endpoint: POST /api/v1/customer/reservations/preview
     * @Description: Tính toán và xem trước giá tiền đỗ xe dự kiến (không lưu vào DB)
     */
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<BigDecimal>> previewReservationPrice(
            @RequestBody Map<String, Object> requestBody) {
        try {
            Long vehicleTypeId = Long.valueOf(requestBody.get("vehicleTypeId").toString());
            Integer durationMinutes = Integer.valueOf(requestBody.get("expectedDurationMinutes").toString());
            LocalDateTime entryTime = LocalDateTime.parse(requestBody.get("expectedEntryTime").toString());

            BigDecimal fee = reservationService.previewPrice(vehicleTypeId, entryTime, durationMinutes);
            return ResponseEntity.ok(ApiResponse.success(fee, "Price preview calculated"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid input data: " + e.getMessage()));
        }
    }

    /**
     * @Endpoint: POST /api/v1/customer/reservations
     * @Description: Tạo mới một đặt chỗ (Pre-booking)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationDTO>> createReservation(
            @RequestBody CreateReservationRequest request) {
        try {
            ReservationDTO result = reservationService.createReservation(request);
            return ResponseEntity.ok(ApiResponse.success(result, "Reservation created successfully"));
        } catch (IllegalStateException e) {
            // Lỗi nghiệp vụ (ví dụ: bãi đầy, xe đang có đơn PENDING)
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            // Lỗi hệ thống khác
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Internal Server Error: " + e.getMessage()));
        }
    }

    /**
     * @Endpoint: PUT /api/v1/customer/reservations/{id}/plate
     * @Description: Khách hàng đổi biển số xe sau khi đã đặt chỗ
     */
    @PutMapping("/{id}/plate")
    public ResponseEntity<ApiResponse<ReservationDTO>> updatePlate(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        try {
            String newPlate = payload.get("plate");
            if (newPlate == null || newPlate.isBlank()) {
                throw new IllegalArgumentException("Plate is required in body");
            }

            ReservationDTO result = reservationService.updateReservationPlate(id, newPlate);
            return ResponseEntity.ok(ApiResponse.success(result, "Vehicle plate updated successfully"));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Internal Server Error: " + e.getMessage()));
        }
    }

    /**
     * @Endpoint: PUT /api/v1/customer/reservations/{id}/cancel
     * @Description: Khách hàng yêu cầu hủy đặt chỗ và nhận hoàn tiền (nếu có)
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationDTO>> cancelReservation(
            @PathVariable Long id,
            @RequestBody CancelReservationRequest request) {
        try {
            ReservationDTO result = reservationService.cancelReservation(id, request);
            return ResponseEntity.ok(ApiResponse.success(result, "Reservation cancelled successfully"));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Internal Server Error: " + e.getMessage()));
        }
    }

    /**
     * @Endpoint: POST /api/v1/customer/reservations/{id}/resolve-conflict
     * @Description: Dành cho Staff bấm vào để xác nhận đã dọn dẹp bãi xe bị đầy
     */
    @PostMapping("/{id}/resolve-conflict")
    public ResponseEntity<ApiResponse<String>> resolveConflict(@PathVariable Long id) {
        try {
            reservationConflictScheduler.attemptResolveConflict(id);
            return ResponseEntity.ok(ApiResponse.success("Success", "Virtual slot reserved successfully"));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Internal Server Error: " + e.getMessage()));
        }
    }
}
