/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-10
 * @Description: REST controller for configuring automatic zone-routing rules. Exposes endpoints
 *               consumed by the manager's Vehicle Routing screen to view and save the routing chain.
 * @Dependencies:
 * - RoutingRuleService (com.pbms.modules.infrastructure.service.RoutingRuleService)
 * - ApiResponse (com.pbms.common.dto.ApiResponse)
 */
package com.pbms.modules.infrastructure.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO (DATA TRANSFER OBJECT)
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho mọi Response trả về Client (luôn có statusCode/message/data).
import com.pbms.modules.infrastructure.dto.RoutingRuleDTO; // Gói dữ liệu cấu hình luật điều phối (cả 2 chiều Request/Response).

// =========================================================================
// PHẦN 2: LỚP SERVICE (CHỨA LOGIC NGHIỆP VỤ)
// =========================================================================
import com.pbms.modules.infrastructure.service.RoutingRuleService; // Bộ não xử lý đọc/ghi cấu hình luật, xem chi tiết ở file Service.

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK, SPRING BOOT VÀ AUDIT LOG
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự sinh Constructor cho field "final" (Constructor Injection gọn).
import org.springframework.http.ResponseEntity; // Lớp bọc của Spring đại diện cho 1 gói tin HTTP trả về (mã 200, 400...).
import org.springframework.web.bind.annotation.*; // Chứa các annotation @RestController, @GetMapping, @PutMapping...
import com.pbms.common.annotation.LogAudit; // Annotation tự định nghĩa, đánh dấu hàm nào cần tự động ghi log lịch sử thao tác (ai sửa, sửa gì, lúc nào).

import java.util.List;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO MÀN HÌNH CẤU HÌNH LUẬT ĐIỀU PHỐI (ROUTING RULE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này chỉ đóng vai trò "Lễ tân" — nhận Request từ màn hình quản lý
 * cấu hình (VehicleRoutingScreen.tsx phía Frontend), đẩy việc thật sự cho
 * `RoutingRuleService` xử lý, rồi đóng gói kết quả trả về theo chuẩn chung
 * `ApiResponse` của dự án.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: `@RequestMapping("/api/v1/manager/routing-rules")` (dòng 33)
 *   là gốc URL — chỉ có màn hình dành cho MANAGER mới gọi tới các API này (khác
 *   với API của thiết bị IoT hay của Gate console).
 * - Minh chứng 2: `@LogAudit(...)` (dòng 47) gắn trên hàm `updateRoutingRules`
 *   — mỗi khi hàm này chạy xong, hệ thống tự động ghi lại 1 dòng lịch sử
 *   "Ai đã sửa cấu hình luật điều phối, lúc nào" mà Controller không cần tự
 *   viết code ghi log thủ công (xử lý ngầm qua AOP/Interceptor).
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/manager/routing-rules")
@RequiredArgsConstructor
public class RoutingRuleController {

    // Bộ não xử lý nghiệp vụ, được Spring tiêm vào qua Constructor (nhờ Lombok @RequiredArgsConstructor).
    private final RoutingRuleService routingRuleService;

    /**
     * =========================================================================
     * API: LẤY CẤU HÌNH LUẬT ĐIỀU PHỐI HIỆN TẠI (ĐỂ HIỂN THỊ LÊN MÀN HÌNH)
     * =========================================================================
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại gốc "/api/v1/manager/routing-rules".
     * 2. Đọc tham số `vehicleType` trên URL (mặc định "CAR" nếu không truyền)
     *    và `floorId` (không bắt buộc — null nghĩa là lấy tất cả các tầng).
     * 3. Gọi Service dựng lại cấu hình theo đúng loại xe + tầng đó.
     * 4. Bọc kết quả vào ApiResponse và trả về mã HTTP 200 OK.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutingRuleDTO>>> getRoutingRules(
            @RequestParam(defaultValue = "CAR") String vehicleType,
            @RequestParam(required = false) Long floorId) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.getRoutingRulesByVehicleTypeAndFloor(vehicleType, floorId),
                "Success"
        ));
    }

    /**
     * =========================================================================
     * API: LƯU (GHI ĐÈ) TOÀN BỘ CẤU HÌNH LUẬT ĐIỀU PHỐI
     * =========================================================================
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP PUT tại gốc "/api/v1/manager/routing-rules".
     * 2. Bóc tách JSON gửi lên thành `RoutingRuleDTO.BatchUpdateRequest` (chứa
     *    loại xe, tầng, và toàn bộ danh sách khung giờ/luật muốn lưu).
     * 3. `@LogAudit` tự động chụp lại "trước/sau" của thao tác này để phục vụ
     *    tra cứu lịch sử thay đổi cấu hình sau này.
     * 4. Gọi Service thực hiện Soft-delete luật cũ + ghi luật mới (chi tiết
     *    xem `RoutingRuleService.updateRoutingRules`).
     * 5. Bọc kết quả (cấu hình mới nhất sau khi lưu) vào ApiResponse và trả về
     *    HTTP 200 OK.
     */
    @PutMapping
    @LogAudit(action = "UPDATE", resource = "RoutingRule", description = "Update routing rules")
    public ResponseEntity<ApiResponse<List<RoutingRuleDTO>>> updateRoutingRules(@RequestBody RoutingRuleDTO.BatchUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.updateRoutingRules(request),
                "Routing rules updated successfully"
        ));
    }
}
