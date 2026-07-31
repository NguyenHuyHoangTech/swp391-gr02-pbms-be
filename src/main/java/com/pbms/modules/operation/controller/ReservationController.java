/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: REST Controller cung cấp các API (Endpoints) cho tính năng đặt chỗ trước (Prebooking Reservation).
 *               Nhận các HTTP Request từ phía người dùng, gọi ReservationService để xử lý logic và trả về HTTP Response.
 * @Dependencies: 
 * - ReservationService: Dùng để thực thi logic nghiệp vụ.
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
     * API: Lấy danh sách tất cả các đơn đặt chỗ.
     * Logic mã giả:
     * 1. Gọi reservationService.getAllReservations() để lấy danh sách DTO.
     * 2. Bọc danh sách vào ApiResponse và trả về cho client với mã HTTP 200 (OK).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> getAllReservations() {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getAllReservations(), "Lấy dữ liệu thành công"));
    }

    /**
     * API: Tạo một đơn đặt chỗ mới.
     * Logic mã giả:
     * 1. Nhận payload JSON từ người dùng và map vào CreateReservationRequest.
     * 2. Gọi hàm createReservation của service để xử lý nghiệp vụ đặt chỗ.
     * 3. Nếu thành công, trả về DTO với HTTP 200.
     * 4. Nếu có lỗi IllegalStateException (lỗi nghiệp vụ, ví dụ: bãi đầy, xe bị blacklist), trả về HTTP 400.
     * 5. Bắt các ngoại lệ khác và trả về HTTP 400.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationDTO>> createReservation(@RequestBody CreateReservationRequest request) {
        try {
            ReservationDTO dto = reservationService.createReservation(request);
            return ResponseEntity.ok(ApiResponse.success(dto, "Tạo đơn đặt chỗ thành công"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Xem trước giá tiền phí đặt chỗ.
     * Logic mã giả:
     * 1. Trích xuất thông tin loại phương tiện, thời gian dự kiến vào và thời gian gửi từ request body.
     * 2. Gọi reservationService.previewPrice để tính toán giá tiền.
     * 3. Trả về giá tiền tính toán được cho người dùng.
     */
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> previewReservationPrice(@RequestBody java.util.Map<String, Object> requestBody) {
        try {
            Long vehicleTypeId = Long.valueOf(requestBody.get("vehicleTypeId").toString());
            Integer expectedDurationMinutes = Integer.valueOf(requestBody.get("expectedDurationMinutes").toString());
            java.time.LocalDateTime expectedEntryTime = java.time.LocalDateTime.parse(requestBody.get("expectedEntryTime").toString());
            
            java.math.BigDecimal fee = reservationService.previewPrice(vehicleTypeId, expectedEntryTime, expectedDurationMinutes);
            return ResponseEntity.ok(ApiResponse.success(fee, "Tính toán thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Hủy đơn đặt chỗ.
     * Logic mã giả:
     * 1. Lấy ID của đơn đặt chỗ từ URL.
     * 2. Lấy lý do hủy từ request body.
     * 3. Gọi hàm cancelReservation của service.
     * 4. Trả về kết quả sau khi hủy thành công hoặc các mã lỗi 400/404 nếu không tìm thấy đơn/không thể hủy.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationDTO>> cancelReservation(
            @PathVariable Long id, 
            @RequestBody CancelReservationRequest request) {
        try {
            ReservationDTO dto = reservationService.cancelReservation(id, request);
            return ResponseEntity.ok(ApiResponse.success(dto, "Hủy đơn thành công"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Cập nhật biển số xe của đơn đặt chỗ.
     * Logic mã giả:
     * 1. Lấy biển số xe mới từ payload. Kiểm tra nếu rỗng thì ném lỗi.
     * 2. Gọi hàm updateReservationPlate của service.
     * 3. Trả về đơn đặt chỗ đã cập nhật.
     */
    @PutMapping("/{id}/plate")
    public ResponseEntity<ApiResponse<ReservationDTO>> updatePlate(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String newPlate = payload.get("plate");
            if (newPlate == null || newPlate.isBlank()) {
                throw new IllegalArgumentException("Vui lòng cung cấp biển số xe");
            }
            ReservationDTO dto = reservationService.updateReservationPlate(id, newPlate);
            return ResponseEntity.ok(ApiResponse.success(dto, "Cập nhật biển số thành công"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * API: Xử lý khi đơn đặt chỗ bị xung đột (VD: bãi đã đầy vật lý lúc xe đến).
     * Logic mã giả:
     * 1. Gọi service để cố gắng tìm chỗ thay thế hoặc cấp chỗ đậu ảo.
     * 2. Trả về thông báo thành công.
     */
    @PostMapping("/{id}/resolve-conflict")
    public ResponseEntity<ApiResponse<String>> resolveConflict(@PathVariable Long id) {
        try {
            reservationService.attemptResolveConflict(id);
            return ResponseEntity.ok(ApiResponse.success("Đã xử lý xung đột thành công", "Thành công"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "Lỗi: " + e.getMessage()));
        }
    }
}