/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-05
 * @Description: REST controller for managing Zone operations. Exposes endpoints for frontend map rendering.
 * @Dependencies:
 * - ZoneService (com.pbms.modules.infrastructure.service.ZoneService)
 * - ApiResponse (com.pbms.common.dto.ApiResponse)
 */
package com.pbms.modules.infrastructure.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ RESPONSE
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho phản hồi HTTP từ Backend trả về Client.
import com.pbms.modules.infrastructure.dto.ZoneDTO; // Gói dữ liệu chuyển tiếp thông tin khu vực đậu xe và sức chứa.

// =========================================================================
// PHẦN 2: LỚP SERVICE VÀ CÁC THƯ VIỆN SPRING BOOT / LOMBOK
// =========================================================================
import com.pbms.modules.infrastructure.service.ZoneService; // Chuyên viên tính toán trạng thái và số lượng ô đỗ thực tế của từng khu vực.
import org.springframework.http.ResponseEntity; // Lớp bọc của Spring đại diện cho phản hồi HTTP kèm status code.
import org.springframework.web.bind.annotation.GetMapping; // Annotation định nghĩa endpoint GET.
import org.springframework.web.bind.annotation.RequestMapping; // Annotation ánh xạ gốc URL cho Controller.
import org.springframework.web.bind.annotation.RestController; // Annotation định nghĩa REST Controller.
import lombok.RequiredArgsConstructor; // Lombok tự động tạo Constructor cho các final field.
import java.util.List;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO QUẢN LÝ KHU VỰC ĐẬU XE (ZONE CONTROLLER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này chịu trách nhiệm cung cấp danh sách toàn bộ khu vực đậu xe (Zone)
 * kèm theo tính toán thời gian thực về tổng sức chứa, số chỗ còn trống, và số xe
 * đã đặt trước (pending reservations) để hiển thị lên bản đồ trực quan.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: `@RequestMapping("/api/v1/infrastructure/zones")` là endpoint
 *   chuẩn phục vụ tính năng giám sát khu vực.
 * - Minh chứng 2: Hàm `getMapZones()` gọi trực tiếp `zoneService.getMapZones()`,
 *   nơi tính toán động (dynamic calculation) số chỗ trống thực tế bằng SQL GROUP BY
 *   thay vì lưu trữ static trong DB, đảm bảo luôn chính xác.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/infrastructure/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    /**
     * =========================================================================
     * API: LẤY DANH SÁCH KHU VỰC VÀ TRẠNG THÁI Ô ĐỖ CHO BẢN ĐỒ
     * =========================================================================
     * MỤC ĐÍCH:
     * Trả về danh sách ZoneDTO chứa đầy đủ thông tin tọa độ, sức chứa tổng,
     * số ô đỗ trống và số lượng xe đặt trước của từng khu vực.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại endpoint `/api/v1/infrastructure/zones/map`.
     * 2. Gọi `zoneService.getMapZones()` để truy vấn và tính toán số liệu.
     * 3. Bọc danh sách kết quả vào ApiResponse với HTTP status 200 OK.
     */
    @GetMapping("/map")
    public ResponseEntity<ApiResponse<List<ZoneDTO>>> getMapZones() {
        // Truy vấn toàn bộ khu vực bãi đỗ (Zone) cùng với tính toán động thời gian thực
        // về số ô đỗ vật lý còn trống và trừ đi các suất đặt trước (pending reservations)
        // đang sắp tới giờ để UI vẽ sơ đồ và hiển thị cảnh báo sức chứa chính xác nhất.
        return ResponseEntity.ok(ApiResponse.success(zoneService.getMapZones(), "Fetched map zones"));
    }
}

