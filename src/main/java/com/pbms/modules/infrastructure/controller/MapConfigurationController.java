/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-06
 * @Description: Controller for retrieving and updating map configurations.
 * @Dependencies: MapConfigurationService
 */
package com.pbms.modules.infrastructure.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ RESPONSE
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho mọi phản hồi HTTP từ Backend trả về Client.
import com.pbms.modules.infrastructure.dto.config.MapConfigDTO; // Gói dữ liệu tổng hợp toàn bộ cấu hình sơ đồ tầng, khu vực, ô đỗ và cổng.

// =========================================================================
// PHẦN 2: LỚP SERVICE (CHỨA LOGIC NGHIỆP VỤ)
// =========================================================================
import com.pbms.modules.infrastructure.service.MapConfigurationService; // Chuyên viên xử lý đọc/ghi cấu hình sơ đồ bãi xe trong Database.

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK, SPRING BOOT VÀ AUDIT LOG
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự sinh Constructor cho các field final.
import lombok.extern.slf4j.Slf4j; // Lombok tự sinh logger để ghi log hệ thống.
import org.springframework.http.ResponseEntity; // Lớp bọc của Spring đại diện cho phản hồi HTTP (200 OK, 400 Bad Request...).
import org.springframework.web.bind.annotation.*; // Các annotation cho REST Controller và ánh xạ endpoint.
import com.pbms.common.annotation.LogAudit; // Annotation ghi vết lịch sử tự động (ai đã sửa sơ đồ, lúc nào).

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO QUẢN LÝ CẤU HÌNH SƠ ĐỒ BÃI XE (MAP CONFIGURATION CONTROLLER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này chịu trách nhiệm cung cấp dữ liệu cấu hình sơ đồ bãi xe (Floor, Zone,
 * Slot, Gate) cho giao diện người dùng và lưu lại cấu hình mới khi quản lý chỉnh sửa
 * trên màn hình thiết kế sơ đồ bãi xe (SpaceMapScreen).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: `@RequestMapping("/api/v1/infrastructure/map")` là gốc URL thuộc
 *   module hạ tầng (infrastructure), phục vụ việc render bản đồ bãi đỗ xe.
 * - Minh chứng 2: `@LogAudit(action = "UPDATE", ...)` gắn trên endpoint POST `/save`
 *   giúp tự động lưu lại lịch sử chỉnh sửa sơ đồ bãi xe vào bảng audit log mà
 *   không cần viết code thủ công trong Controller.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/infrastructure/map")
@RequiredArgsConstructor
@Slf4j
public class MapConfigurationController {

    private final MapConfigurationService mapConfigurationService;

    /**
     * =========================================================================
     * API: LẤY CẤU HÌNH SƠ ĐỒ BÃI XE HIỆN TẠI
     * =========================================================================
     * MỤC ĐÍCH:
     * Trả về toàn bộ cấu trúc tầng (Floor), khu vực (Zone), ô đỗ (Slot) và cổng
     * (Gate) đang hoạt động trong bãi xe để Frontend vẽ sơ đồ trực quan.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại endpoint `/api/v1/infrastructure/map/config`.
     * 2. Gọi `mapConfigurationService.getMapConfiguration()` để tổng hợp cấu hình.
     * 3. Bọc kết quả trong ApiResponse và trả về mã HTTP 200 OK.
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<MapConfigDTO>> getMapConfig() {
        // Truy vấn toàn bộ cấu trúc sơ đồ bãi xe (Tầng, Khu vực, Cổng, Ô đỗ, Loại xe)
        // để chuyển cho Frontend vẽ giao diện SpaceMapScreen.
        return ResponseEntity.ok(ApiResponse.success(
                mapConfigurationService.getMapConfiguration(),
                "Successfully retrieved parking configuration"
        ));
    }

    /**
     * =========================================================================
     * API: LƯU (GHI ĐÈ) CẤU HÌNH SƠ ĐỒ BÃI XE MỚI
     * =========================================================================
     * MỤC ĐÍCH:
     * Nhận gói dữ liệu MapConfigDTO từ Frontend khi Quản lý lưu sơ đồ mới, kiểm
     * tra tính hợp lệ và ghi đè cấu hình vào Database.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP POST tại endpoint `/api/v1/infrastructure/map/save`.
     * 2. Kích hoạt `@LogAudit` chụp lại thay đổi cấu hình sơ đồ bãi xe.
     * 3. Gọi `mapConfigurationService.saveMapConfiguration(mapConfigDTO)` để
     *    cập nhật/thêm mới/soft-delete các tầng, khu vực, ô đỗ và cổng.
     * 4. Nếu thành công: trả về HTTP 200 OK.
     * 5. Nếu xảy ra lỗi (ví dụ xóa khu vực đang có xe đỗ): bắt Exception, ghi
     *    log và trả về HTTP 400 Bad Request kèm thông báo lỗi.
     */
    @PostMapping("/save")
    @LogAudit(action = "UPDATE", resource = "MapConfiguration", description = "Update space map configuration")
    public ResponseEntity<ApiResponse<String>> saveMapConfig(@RequestBody MapConfigDTO mapConfigDTO) {
        try {
            // Ghi nhận và áp dụng toàn bộ thay đổi cấu hình sơ đồ trong 1 giao dịch (Transactional).
            // Nếu phát hiện khu vực bị xóa đang có xe gửi (ACTIVE Session), Service sẽ ném ngoại lệ
            // để bảo vệ dữ liệu vận hành không bị đứt gãy.
            mapConfigurationService.saveMapConfiguration(mapConfigDTO);
            return ResponseEntity.ok(ApiResponse.success("OK", "Map configuration saved successfully"));
        } catch (Exception e) {
            // Ghi vết lỗi vào hệ thống log và trả về HTTP 400 kèm nguyên nhân chi tiết
            // để Frontend hiển thị thông báo hợp lý cho Quản lý bãi xe.
            log.error("Failed to parse string list map configuration", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
