/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: REST controller exposing the staff duty shift APIs: open shift, close shift,
 *               read the current shift, preview the settlement figures, and query shift history.
 * @Dependencies:
 * - WorkSessionService (com.pbms.modules.identity.service.WorkSessionService)
 * - StaffWorkSession (com.pbms.modules.identity.domain.StaffWorkSession)
 * - ApiResponse (com.pbms.common.dto.ApiResponse)
 */
package com.pbms.modules.identity.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ RESPONSE
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho phản hồi HTTP từ Backend trả về Client.

// =========================================================================
// PHẦN 2: CÁC DOMAIN ENTITY VÀ SERVICE LÕI
// =========================================================================
import com.pbms.modules.identity.domain.StaffWorkSession; // Entity ca trực của nhân viên tại cổng.
import com.pbms.modules.identity.service.WorkSessionService; // Dịch vụ quản lý ca trực, mở/đóng ca và chốt doanh thu.

// =========================================================================
// PHẦN 3: CÁC THƯ VIỆN SPRING BOOT / LOMBOK VÀ PHÂN TRANG
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự động tạo Constructor cho các final field.
import org.springframework.http.ResponseEntity; // Lớp bọc phản hồi HTTP kèm status code.
import org.springframework.security.core.Authentication; // Thông tin người dùng đăng nhập hiện tại từ Spring Security.
import org.springframework.web.bind.annotation.*; // Các annotation cho REST Controller và ánh xạ HTTP.
import org.springframework.data.domain.PageRequest; // Đối tượng yêu cầu phân trang.
import org.springframework.data.domain.Pageable; // Giao diện phân trang của Spring Data.
import org.springframework.data.domain.Sort; // Định nghĩa quy tắc sắp xếp.

import java.util.Map;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO QUẢN LÝ CA TRỰC NHÂN VIÊN (WORK SESSION CONTROLLER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này chịu trách nhiệm cung cấp các API cho Nhân viên kiểm soát cổng
 * thực hiện thao tác Mở ca trực (start), Đóng ca trực (end), kiểm tra trạng thái
 * ca hiện tại (current), xem trước doanh thu chốt ca (preview-settlement), và cho
 * phép Quản lý xem lịch sử ca làm việc (history).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Tích hợp chặt chẽ với `Authentication` từ Spring Security để định
 *   danh chính xác nhân viên thao tác qua email trong JWT token — Client KHÔNG được
 *   phép tự khai email của mình, tránh mạo danh mở/đóng ca của người khác.
 * - Minh chứng 2: Toàn bộ endpoint đều bọc `ApiResponse` để Frontend nhận cùng 1
 *   định dạng phản hồi thống nhất trên mọi module.
 * - Minh chứng 3: Endpoint `/current` cố ý KHÔNG trả HTTP 400 khi không có ca trực,
 *   mà trả về dữ liệu rỗng hợp lệ — để giao diện vẫn cho nhân viên mở ca mới thay
 *   vì hiện màn hình lỗi.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/identity/work-sessions")
@RequiredArgsConstructor
public class WorkSessionController {

    private final WorkSessionService workSessionService;

    /**
     * =========================================================================
     * API: MỞ CA TRỰC TẠI CỔNG BÃI XE (START WORK SESSION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Cho phép Nhân viên chọn một Cổng kiểm soát (Gate) và vai trò trực
     * (ENTRY/EXIT) để bắt đầu ca làm việc.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP POST tại endpoint `/api/v1/identity/work-sessions/start`.
     * 2. Trích xuất email nhân viên từ `Authentication` và `gateId`, `gateType` từ body.
     * 3. Gọi `workSessionService.startSession(email, gateId, gateType)` để khởi
     *    tạo phiên làm việc mới (chỉ thành công nếu nhân viên và cổng đều chưa
     *    có ca trực ACTIVE nào khác).
     * 4. Trả về map thông tin phiên trực vừa mở kèm HTTP 200 OK.
     * 5. Nếu vi phạm ràng buộc độc quyền -> Trả HTTP 400 kèm lý do cụ thể.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startSession(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        try {
            // Lấy email người dùng từ Security Context để bảo đảm tính xác thực
            String email = authentication.getName();
            Long gateId = Long.valueOf(body.get("gateId").toString());
            String gateType = body.get("gateType") != null ? body.get("gateType").toString() : null;

            StaffWorkSession session = workSessionService.startSession(email, gateId, gateType);

            Map<String, Object> result = Map.of(
                    "sessionId", session.getId(),
                    "gateId", session.getGate().getId(),
                    "gateName", session.getGate().getGateName(),
                    "gateType", session.getWorkGateType(),
                    "loginTime", session.getLoginTime(),
                    "status", session.getStatus());
            return ResponseEntity.ok(ApiResponse.success(result, "Work session started successfully"));
        } catch (Exception e) {
            // Trả HTTP 400 kèm lý do lỗi (ví dụ: cổng đang có người khác trực)
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API: ĐÓNG CA TRỰC VÀ CHỐT DOANH THU (END WORK SESSION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Kết thúc ca làm việc hiện tại của nhân viên, tự động tính toán tổng doanh
     * thu (tiền mặt/chuyển khoản) từ các giao dịch trong ca để ghi nhận vào CSDL.
     *
     * LƯU Ý KIẾN TRÚC:
     * Hệ thống KHÔNG tin vào số tiền do Frontend khai báo — doanh thu được tính
     * lại từ bảng `transactions` trong DB để tránh sai lệch và gian lận khi đối
     * soát tiền mặt nhân viên nộp lại.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP PUT tại endpoint `/api/v1/identity/work-sessions/end`.
     * 2. Lấy email nhân viên từ `Authentication`.
     * 3. Gọi `workSessionService.endSession(email)` để chốt ca và cập nhật trạng
     *    thái về "COMPLETED", đồng thời trả cổng về trạng thái "IDLE".
     * 4. Trả về kết quả chốt ca thành công HTTP 200 OK.
     */
    @PutMapping("/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endSession(Authentication authentication) {
        try {
            String email = authentication.getName();
            Map<String, Object> result = workSessionService.endSession(email);
            return ResponseEntity.ok(ApiResponse.success(result, "Work session closed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API: KIỂM TRA TRẠNG THÁI CA TRỰC HIỆN TẠI (GET CURRENT SESSION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Kiểm tra xem nhân viên đăng nhập hiện tại có ca làm việc nào đang ACTIVE
     * hay không, để giao diện quản lý ca trực và quầy kiểm soát cổng hiển thị
     * đúng trạng thái (khóa hay mở quầy).
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại endpoint `/api/v1/identity/work-sessions/current`.
     * 2. Gọi `workSessionService.getPreviewSettlement(email)` để tra cứu phiên.
     * 3. Nếu `hasActiveSession = true`: trả về chi tiết ca trực kèm HTTP 200 OK.
     * 4. Nếu không có hoặc lỗi: trả về kết quả rỗng (null) hợp lệ cho Frontend.
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentSession(Authentication authentication) {
        try {
            String email = authentication.getName();
            Map<String, Object> preview = workSessionService.getPreviewSettlement(email);
            if (Boolean.TRUE.equals(preview.get("hasActiveSession"))) {
                return ResponseEntity.ok(ApiResponse.success(preview, "Found active session"));
            }
            return ResponseEntity.ok(ApiResponse.success(null, "No active session"));
        } catch (Exception e) {
            // Xử lý mềm dẻo: không ném 400 mà trả null để UI cho phép mở ca mới
            return ResponseEntity.ok(ApiResponse.success(null, "No active session"));
        }
    }

    /**
     * =========================================================================
     * API: XEM TRƯỚC DOANH THU CHỐT CA (PREVIEW SETTLEMENT)
     * =========================================================================
     * MỤC ĐÍCH:
     * Trả về báo cáo tổng kết tạm thời (tổng số lượt vào/ra, doanh thu tiền mặt,
     * doanh thu chuyển khoản) trước khi nhân viên xác nhận bấm nút Đóng ca.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại `/api/v1/identity/work-sessions/current/preview-settlement`.
     * 2. Gọi `workSessionService.getPreviewSettlement(email)`.
     * 3. Trả về bảng tổng hợp số liệu cho hộp thoại xác nhận chốt ca trên UI.
     */
    @GetMapping("/current/preview-settlement")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewSettlement(Authentication authentication) {
        try {
            String email = authentication.getName();
            Map<String, Object> preview = workSessionService.getPreviewSettlement(email);
            return ResponseEntity.ok(ApiResponse.success(preview, "Fetched preview settlement"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API: LẤY LỊCH SỬ CÁC CA TRỰC ĐÃ HOÀN THÀNH (GET WORK SESSION HISTORY)
     * =========================================================================
     * MỤC ĐÍCH:
     * Hỗ trợ Quản lý bãi xe tra cứu danh sách lịch sử làm việc theo thời gian và
     * theo vai trò cổng, có hỗ trợ phân trang và sắp xếp.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại `/api/v1/identity/work-sessions/history`.
     * 2. Nhận các tham số lọc `startDate`, `endDate`, `gateType`, và thông số
     *    phân trang `page`, `size` (mặc định trang 0, 10 bản ghi).
     * 3. Sắp xếp giảm dần theo `logoutTime` để ca vừa chốt nằm trên đầu.
     * 4. Gọi `workSessionService.getWorkSessionHistory(...)`.
     * 5. Trả về đối tượng Page chứa danh sách lịch sử cho UI hiển thị bảng.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Object>> getHistory(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String gateType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // Ca trực mới chốt (logoutTime gần nhất) luôn nằm trên đầu danh sách
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "logoutTime"));
            Object history = workSessionService.getWorkSessionHistory(startDate, endDate, gateType, pageable);
            return ResponseEntity.ok(ApiResponse.success(history, "Fetched work session history successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
}
