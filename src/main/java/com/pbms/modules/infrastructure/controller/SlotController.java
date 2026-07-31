/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller nhận sự kiện cảm biến IoT khi 1 chỗ đỗ đổi trạng
 * thái, và cho phép nhân viên/manager đổi trạng thái slot thủ công (VD bật
 * chế độ bảo trì) từ màn hình quản lý.
 * @Dependencies:
 * - SlotRepository (Local)
 * - ZoneRoutingService, ZoneOccupancyTracker (Local)
 */
package com.pbms.modules.infrastructure.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ RESPONSE
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho phản hồi HTTP từ Backend trả về Client.

// =========================================================================
// PHẦN 2: REPOSITORY, ENTITY VÀ WEBSOCKET
// =========================================================================
import com.pbms.modules.infrastructure.domain.Slot; // Entity ánh xạ bảng `slots` trong Database.
import com.pbms.modules.infrastructure.repository.SlotRepository; // Kho truy vấn và cập nhật trạng thái ô đỗ.
import org.springframework.messaging.simp.SimpMessagingTemplate; // Công cụ phát sự kiện WebSocket thời gian thực cho Client.

// =========================================================================
// PHẦN 3: LOMBOK, SPRING BOOT VÀ AUDIT ANNOTATION
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự sinh Constructor cho các field final.
import org.springframework.http.ResponseEntity; // Lớp bọc của Spring đại diện cho phản hồi HTTP.
import org.springframework.web.bind.annotation.*; // Các annotation định nghĩa REST Controller và endpoint.
import com.pbms.common.annotation.LogAudit; // Annotation tự động lưu lại lịch sử thay đổi vào database.
import java.util.Map;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO QUẢN LÝ Ô ĐỖ XE (SLOT CONTROLLER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này chịu trách nhiệm cho phép cập nhật trạng thái ô đỗ xe (EMPTY hoặc
 * DISABLED - Khóa bảo trì) từ giao diện quản trị, đồng thời phát tín hiệu WebSocket
 * để cập nhật sơ đồ bãi xe thời gian thực (Real-time) cho toàn bộ nhân viên.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: `@RequestMapping("/api/v1/infrastructure/slots")` là endpoint
 *   quản lý ô đỗ thuộc module hạ tầng (infrastructure).
 * - Minh chứng 2: Giao tiếp qua `messagingTemplate.convertAndSend("/topic/map-updates", slot)`
 *   đảm bảo ngay sau khi sửa trạng thái ô đỗ, màn hình Sơ đồ bãi xe (`SpaceMapScreen.tsx`)
 *   sẽ tự động vẽ lại mà không cần tải lại trang.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/infrastructure/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotRepository slotRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * =========================================================================
     * API: CẬP NHẬT TRẠNG THÁI Ô ĐỖ XE (THỦ CÔNG TỪ UI)
     * =========================================================================
     * MỤC ĐÍCH:
     * Cho phép quản lý hoặc nhân viên chuyển trạng thái ô đỗ giữa TRỐNG (EMPTY)
     * và BẢO TRÌ/KHOÁ (DISABLED).
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP PUT tại endpoint `/api/v1/infrastructure/slots/{id}/status`.
     * 2. Tìm Slot theo ID trong database qua `slotRepository.findById(id)`.
     * 3. Kiểm tra tính hợp lệ của trạng thái mới (chỉ chấp nhận "EMPTY" hoặc "DISABLED").
     * 4. Cập nhật trạng thái mới cho Slot và lưu vào Database.
     * 5. Phát tín hiệu WebSocket tới kênh `/topic/map-updates` chứa đối tượng Slot
     *    để các giao diện kết nối nhận được sự kiện cập nhật thời gian thực.
     * 6. Trả về phản hồi thành công HTTP 200 OK.
     */
    @PutMapping("/{id}/status")
    @LogAudit(action = "UPDATE", resource = "Slot", description = "Update slot status manually")
    public ResponseEntity<ApiResponse<String>> updateSlotStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // Tìm ô đỗ xe (Slot) trong CSDL theo ID, nếu không tồn tại thì báo lỗi ngay
        // để tránh thao tác trên đối tượng rỗng.
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));
        
        // Chỉ chấp nhận 2 trạng thái thủ công: EMPTY (trống rỗng) hoặc DISABLED (khóa/bảo trì).
        // Các trạng thái như OCCUPIED hay RESERVED phải do hệ thống tự động gán thông qua
        // các luồng nghiệp vụ gửi xe hoặc đặt chỗ trước để đảm bảo tính toàn vẹn dữ liệu.
        String status = body.get("status");
        if (status == null || (!status.equals("EMPTY") && !status.equals("DISABLED"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid status"));
        }
        
        slot.setStatus(status);
        slotRepository.save(slot);
        
        // LƯU Ý KIẾN TRÚC: Phát sự kiện qua WebSocket tới kênh `/topic/map-updates`.
        // Nhờ tín hiệu này, màn hình Sơ đồ bãi xe (SpaceMapScreen.tsx) của Quản lý
        // và toàn bộ Nhân viên lập tức chuyển màu ô đỗ mà không cần làm mới trang.
        messagingTemplate.convertAndSend("/topic/map-updates", slot);
        
        return ResponseEntity.ok(ApiResponse.success("Updated", "Success"));
    }
}

