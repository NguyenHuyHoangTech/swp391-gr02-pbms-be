/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller quản lý kho thẻ RFID (màn hình Card Warehouse của
 * manager) - liệt kê thẻ, đổi trạng thái thủ công, import CSV hàng loạt.
 * @Dependencies:
 * - RfidCardService (Local)
 */
package com.pbms.modules.infrastructure.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ RESPONSE
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho phản hồi HTTP từ Backend trả về Client.
import com.pbms.modules.infrastructure.dto.RfidCardDTO; // Gói dữ liệu chuyển tiếp thông tin thẻ RFID (UID, Visual ID, trạng thái).

// =========================================================================
// PHẦN 2: LỚP SERVICE VÀ CÁC THƯ VIỆN SPRING BOOT / LOMBOK
// =========================================================================
import com.pbms.modules.infrastructure.service.RfidCardService; // Chuyên viên nghiệp vụ quản lý thẻ RFID và import từ file CSV.
import lombok.RequiredArgsConstructor; // Lombok tự động tạo Constructor cho các final field.
import org.springframework.http.ResponseEntity; // Lớp bọc của Spring đại diện cho phản hồi HTTP kèm mã status code.
import org.springframework.web.bind.annotation.*; // Các annotation định nghĩa REST Controller và mapping HTTP method.
import com.pbms.common.annotation.LogAudit; // Annotation tự động lưu nhật ký kiểm toán vào database.
import org.springframework.web.multipart.MultipartFile; // Lớp xử lý tệp tin tải lên (CSV, hình ảnh...).

import java.util.List;
import java.util.Map;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO QUẢN LÝ THẺ RFID (RFID CARD CONTROLLER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này chịu trách nhiệm quản lý danh sách thẻ RFID trong bãi đỗ xe,
 * hỗ trợ cập nhật trạng thái thẻ (AVAILABLE, DAMAGED, LOST...) và import hàng loạt
 * danh sách thẻ từ file CSV để tiện lợi cho bộ phận quản lý bãi xe (Manager).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: `@RequestMapping("/api/v1/infrastructure/cards")` là endpoint
 *   chuẩn phục vụ màn hình Quản lý thẻ (`CardManagementScreen.tsx`) bên Frontend.
 * - Minh chứng 2: Gắn `@LogAudit` tại API import và cập nhật trạng thái nhằm bảo
 *   đảm tính minh bạch, truy vết khi một thẻ RFID bị thay đổi trạng thái hoặc import mới.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/infrastructure/cards")
@RequiredArgsConstructor
public class RfidCardController {

    private final RfidCardService rfidCardService;

    /**
     * =========================================================================
     * API: LẤY DANH SÁCH TOÀN BỘ THẺ RFID TRONG HỆ THỐNG
     * =========================================================================
     * MỤC ĐÍCH:
     * Trả về danh sách thẻ RFID cùng trạng thái hiện tại của thẻ (AVAILABLE,
     * IN_USE, DAMAGED, LOST) để hiển thị trên bảng quản lý thẻ.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại endpoint `/api/v1/infrastructure/cards`.
     * 2. Gọi `rfidCardService.getAllCards()` để truy vấn và chuyển đổi danh sách.
     * 3. Bọc kết quả vào ApiResponse với HTTP 200 OK.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RfidCardDTO>>> getAllCards() {
        // Lấy danh sách toàn bộ thẻ RFID trong hệ thống (kèm theo vị trí tương ứng
        // "In Session" hoặc "Gate Staff" được Service quy đổi dựa theo trạng thái thẻ).
        return ResponseEntity.ok(ApiResponse.success(
                rfidCardService.getAllCards(),
                "Fetched RFID cards successfully"
        ));
    }

    /**
     * =========================================================================
     * API: CẬP NHẬT TRẠNG THÁI THẺ RFID (THEO UID)
     * =========================================================================
     * MỤC ĐÍCH:
     * Cho phép Quản lý đổi trạng thái của thẻ (ví dụ báo mất thẻ - LOST, hoặc
     * báo hỏng - DAMAGED, hoặc kích hoạt lại - AVAILABLE).
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP PUT tại endpoint `/api/v1/infrastructure/cards/{uid}/status`.
     * 2. Đọc giá trị `"status"` từ request body.
     * 3. Gọi `rfidCardService.updateStatus(uid, status)` để cập nhật DB.
     * 4. Ghi audit log tự động qua `@LogAudit`.
     * 5. Trả về phản hồi thành công HTTP 200 OK.
     */
    @PutMapping("/{uid}/status")
    @LogAudit(action = "UPDATE", resource = "RfidCard", description = "Update RFID card status")
    public ResponseEntity<ApiResponse<Void>> updateCardStatus(
            @PathVariable String uid,
            @RequestBody Map<String, String> requestBody) {
        // Lấy trạng thái mới (ví dụ AVAILABLE, LOST, DAMAGED) từ payload gửi lên
        // và yêu cầu Service cập nhật vào DB. @LogAudit tự động lưu vết lại.
        String status = requestBody.get("status");
        rfidCardService.updateStatus(uid, status);
        return ResponseEntity.ok(ApiResponse.success(null, "RFID card status updated successfully"));
    }

    /**
     * =========================================================================
     * API: IMPORT DANH SÁCH THẺ RFID TỪ FILE CSV
     * =========================================================================
     * MỤC ĐÍCH:
     * Hỗ trợ Quản lý nhập nhanh hàng chục/hàng trăm thẻ RFID mới vào hệ thống
     * từ tệp tin CSV cấu trúc chuẩn thay vì nhập tay từng thẻ.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP POST tại endpoint `/api/v1/infrastructure/cards/import`.
     * 2. Nhận file upload (`MultipartFile`).
     * 3. Gọi `rfidCardService.importCardsFromCsv(file)` để phân tích CSV và tạo
     *    các bản ghi thẻ RFID mới chưa tồn tại trong DB.
     * 4. Trả về số lượng thẻ import thành công trong ApiResponse.
     */
    @PostMapping("/import")
    @LogAudit(action = "CREATE", resource = "RfidCard", description = "Import RFID cards")
    public ResponseEntity<ApiResponse<Integer>> importCards(@RequestParam("file") MultipartFile file) {
        // Phân tích file CSV tải lên và nạp các thẻ mới chưa tồn tại vào Database,
        // trả về tổng số lượng thẻ đã import thành công cho UI hiển thị thông báo.
        int importedCount = rfidCardService.importCardsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success(importedCount, "RFID cards imported successfully"));
    }
}

