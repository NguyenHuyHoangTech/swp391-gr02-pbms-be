package com.pbms.modules.incident.controller;

/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ REQUEST CỦA QUẢN LÝ SỰ CỐ (KÈM MINH CHỨNG CODE)
 * (Trình bày chi tiết luồng hoạt động từ Frontend/Client tới Service Lõi)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO BỘ ĐIỀU PHỐI VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Khai báo `@RestController`, `@RequestMapping("/api/v1/incident/incidents")` 
 *   và `@RequiredArgsConstructor` ở tầng Class.
 * - Cụ thể: Spring Boot IoC Container tự động phát hiện Controller này, đăng ký làm Rest Endpoint 
 *   và tự động tiêm (Inject) `IncidentService` qua Constructor mà không cần viết code boilerplate.
 * 
 * BƯỚC 2: TIẾP NHẬN REQUEST TỪ FRONTEND (DISPATCHER SERVLET & HANDLER MAPPING)
 * - Minh chứng: Các Annotation ánh xạ HTTP như `@PostMapping`, `@GetMapping`, `@PutMapping("/{id}/resolve")`...
 * - Cụ thể: Khi Frontend gửi Request HTTP GET/POST/PUT tới URL tương ứng, DispatcherServlet định tuyến 
 *   chính xác tới hàm xử lý thích hợp trong Controller này.
 * 
 * BƯỚC 3: RÀNG BUỘC VÀ CHUYỂN ĐỔI DỮ LIỆU (DESERIALIZATION & AUTHENTICATION)
 * - Minh chứng 1: Tham số `@RequestBody IncidentTicketRequest request` hoặc `@RequestBody Map<String, Object> requestBody`.
 *   Jackson ObjectMapper tự động parse chuỗi JSON từ HTTP Request Body thành Java Object.
 * - Minh chứng 2: Tham số `Authentication authentication`. Spring Security tự động tiêm thông tin người dùng 
 *   đang đăng nhập (Email, Roles/Authorities) từ SecurityContext.
 * 
 * BƯỚC 4: ỦY QUYỀN XỬ LÝ NGHIỆP VỤ LÕI (SERVICE DELEGATION - N-TIER ARCHITECTURE)
 * - Controller này tuyệt đối KHÔNG tự tính toán logic hay tương tác Database trực tiếp.
 * - Minh chứng: Mọi endpoint đều ủy quyền trực tiếp cho `incidentService` (VD: `incidentService.createIncident()`,
 *   `incidentService.resolveIncident()`, `incidentService.processPhase1()`).
 * 
 * BƯỚC 5: ĐÓNG GÓI VÀ TRẢ VỀ RESPONSE (SERIALIZATION & HTTP RESPONSE)
 * - Minh chứng: Lệnh `return ResponseEntity.ok(ApiResponse.success(dto, "message"))` hoặc `ResponseEntity.badRequest()`.
 * - Cụ thể: Đóng gói dữ liệu trả về theo chuẩn `ApiResponse` (statusCode, message, data).
 *   Spring Boot chuyển đổi Object Java thành chuỗi JSON qua dây mạng trả về cho Frontend.
 * =========================================================================================
 */

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO VÀ DOMAIN (DATA TRANSFER OBJECTS)
// =========================================================================
import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.incident.domain.IncidentTicket;
import com.pbms.modules.incident.dto.IncidentTicketDTO;
import com.pbms.modules.incident.dto.IncidentTicketRequest;

// =========================================================================
// PHẦN 2: CÁC LỚP SERVICE (CHỨA LOGIC NGHIỆP VỤ LÕI)
// =========================================================================
import com.pbms.modules.incident.service.IncidentService;

// =========================================================================
// PHẦN 3: CÁC THƯ VIỆN SPRING BOOT & LOMBOK
// =========================================================================
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/incident/incidents")
@RequiredArgsConstructor
public class IncidentTicketController {

    // Tiêm phụ thuộc Service xử lý sự cố thông qua Lombok Constructor Injection
    private final IncidentService incidentService;

    /**
     * =========================================================================
     * API 1: TẠO BÁO CÁO SỰ CỐ MỚI (CREATE INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH: Tiếp nhận yêu cầu báo cáo sự cố (Mất thẻ, hỏng thẻ, xe đỗ sai vị
     * trí...)
     * từ phía Khách hàng hoặc Nhân viên bảo vệ tại quầy.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Lấy thông tin email từ Authentication Object (nếu đã đăng nhập).
     * 2. Gọi `incidentService.createIncident(request, email)` để kiểm tra dữ liệu
     * và lưu vào DB.
     * 3. Trả về HTTP 200 OK bọc trong ApiResponse chứa thông tin Ticket vừa tạo.
     * 4. Bắt ngoại lệ if fail -> Trả về HTTP 400 Bad Request kèm thông báo lỗi.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<IncidentTicket>> createIncident(
            @RequestBody IncidentTicketRequest request,
            Authentication authentication) {
        try {
            String email = authentication != null ? authentication.getName() : null;
            IncidentTicket ticket = incidentService.createIncident(request, email);
            return ResponseEntity.ok(ApiResponse.success(ticket, "Incident created successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 2: LẤY DANH SÁCH SỰ CỐ (GET ALL INCIDENTS)
     * =========================================================================
     * MỤC ĐÍCH: Trả về danh sách các sự cố. Nếu là Customer thì chỉ trả về các sự
     * cố
     * do chính họ tạo. Nếu là Staff/Manager thì trả về toàn bộ hàng chờ để xử lý.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Kiểm tra xem quyền (Authorities) của User có phải là ROLE_CUSTOMER không.
     * 2. Nếu là Customer, gán `email = authentication.getName()`. Ngược lại `email
     * = null`.
     * 3. Ủy quyền cho `incidentService.getAllIncidents(email)` để query Database.
     * 4. Trả về HTTP 200 OK kèm danh sách IncidentTicketDTO.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<IncidentTicketDTO>>> getAllIncidents(
            Authentication authentication) {

        boolean isCustomer = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));

        String email = (isCustomer && authentication != null) ? authentication.getName() : null;

        return ResponseEntity.ok(ApiResponse.success(incidentService.getAllIncidents(email), "Fetched successfully"));
    }

    /**
     * =========================================================================
     * API 3: CHUYỂN SỰ CỐ SANG KHU VỰC QUÁ GIỜ (MOVE TO OVERSTAY)
     * =========================================================================
     * MỤC ĐÍCH: Cập nhật trạng thái sự cố đỗ xe quá thời gian quy định sang khu vực
     * Overstay.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Parse `uploadedDocUrl` từ Request Body (ảnh biên bản/chụp xe).
     * 2. Gọi `incidentService.moveToOverstay(id, uploadedDocUrl)`.
     * 3. Trả về HTTP 200 OK kèm DTO đã cập nhật.
     */
    @PutMapping("/{id}/move-to-overstay")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> moveToOverstay(
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        try {
            String uploadedDocUrl = (String) requestBody.get("uploadedDocUrl");
            IncidentTicketDTO dto = incidentService.moveToOverstay(id, uploadedDocUrl);
            return ResponseEntity.ok(ApiResponse.success(dto, "Moved to overstay zone successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 4: XÁC NHẬN ĐÃ ĐỌC THÔNG BÁO QUÁ GIỜ (ACKNOWLEDGE OVERSTAY)
     * =========================================================================
     * MỤC ĐÍCH: Nhân viên bấm xác nhận đã biết về sự cố quá giờ để tiến hành xử lý.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận PathVariable `id`.
     * 2. Gọi `incidentService.acknowledgeOverstay(id)`.
     * 3. Trả về HTTP 200 OK.
     */
    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> acknowledgeOverstay(@PathVariable Long id) {
        try {
            IncidentTicketDTO dto = incidentService.acknowledgeOverstay(id);
            return ResponseEntity.ok(ApiResponse.success(dto, "Incident acknowledged successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 5: HOÀN TẤT XỬ LÝ SỰ CỐ & KẾT TOÁN (RESOLVE INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH: Chốt số tiền (phí gửi xe + phạt - giảm giá), cập nhật trạng thái
     * RESOLVED,
     * và mở đường cho xe xuất bãi.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Trích xuất các tham số từ Body: ghi chú, ảnh biên bản, phí gửi xe, phí
     * phạt, giảm giá, pthức thanh toán, checkoutToken.
     * 2. Nếu `paymentMethod` rỗng -> mặc định gán là "CASH" (Tiền mặt).
     * 3. Ủy quyền cho `incidentService.resolveIncident(...)` để tính toán tài chính
     * và đóng phiên gửi xe.
     * 4. Trả về HTTP 200 OK kèm DTO kết quả.
     */
    @PutMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> resolveIncident(
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        try {
            String resolutionNotes = (String) requestBody.get("resolutionNotes");
            String resolutionImageUrl = (String) requestBody.get("resolutionImageUrl");
            String uploadedPicOutUrl = (String) requestBody.get("uploadedPicOutUrl");
            BigDecimal parkingFee = requestBody.get("parkingFee") != null
                    ? new BigDecimal(requestBody.get("parkingFee").toString())
                    : null;
            BigDecimal penaltyFee = requestBody.get("penaltyFee") != null
                    ? new BigDecimal(requestBody.get("penaltyFee").toString())
                    : null;
            BigDecimal discountAmount = requestBody.get("discountAmount") != null
                    ? new BigDecimal(requestBody.get("discountAmount").toString())
                    : null;
            String paymentMethod = (String) requestBody.get("paymentMethod");
            if (paymentMethod == null || paymentMethod.isBlank()) {
                paymentMethod = "CASH";
            }
            String checkoutToken = (String) requestBody.get("checkoutToken");
            IncidentTicketDTO dto = incidentService.resolveIncident(id,
                    resolutionNotes, resolutionImageUrl, uploadedPicOutUrl, parkingFee, penaltyFee, discountAmount,
                    paymentMethod, null, checkoutToken);
            return ResponseEntity.ok(ApiResponse.success(dto, "Incident resolved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 6: HỦY BỎ SỰ CỐ (CANCEL INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH: Hủy sự cố mà không xử lý/phạt (Ví dụ khách tìm thấy thẻ mất).
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Đọc lý do hủy, loại hủy và ảnh minh chứng từ Request Body.
     * 2. Gọi `incidentService.cancelIncident(...)`.
     * 3. Trả về HTTP 200 OK.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> cancelIncident(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        try {
            String reason = requestBody.get("reason");
            String cancelType = requestBody.get("cancelType");
            String cancelImageUrl = requestBody.get("cancelImageUrl");
            IncidentTicketDTO dto = incidentService.cancelIncident(id, reason, cancelType, cancelImageUrl);
            return ResponseEntity.ok(ApiResponse.success(dto, "Incident cancelled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 8: XÁC NHẬN GIAI ĐOẠN 1 (PROCESS PHASE 1)
     * =========================================================================
     * MỤC ĐÍCH: Nhân viên kiểm tra và chốt mức phạt ban đầu, chuyển sự cố sang Giai
     * đoạn 2.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Parse các tham số ghi chú, ảnh, tiền phạt (fineAmount), giảm giá
     * (discountAmount).
     * 2. Gọi `incidentService.processPhase1(...)`.
     * 3. Trả về HTTP 200 OK chuyển trạng thái sang Phase 2.
     */
    @PutMapping("/{id}/process-phase1")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> processPhase1(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            String resolutionNotes = requestBody != null ? (String) requestBody.get("resolutionNotes") : null;
            String resolutionImageUrl = requestBody != null ? (String) requestBody.get("resolutionImageUrl") : null;
            BigDecimal fineAmount = (requestBody != null && requestBody.get("fineAmount") != null)
                    ? new BigDecimal(requestBody.get("fineAmount").toString())
                    : null;
            BigDecimal discountAmount = (requestBody != null && requestBody.get("discountAmount") != null)
                    ? new BigDecimal(requestBody.get("discountAmount").toString())
                    : null;
            IncidentTicketDTO dto = incidentService.processPhase1(id, resolutionNotes, resolutionImageUrl, fineAmount,
                    discountAmount);
            return ResponseEntity.ok(ApiResponse.success(dto, "Transitioning to the 2nd stage"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 9: TỪ CHỐI BÁO CÁO SỰ CỐ (REJECT INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH: Từ chối báo cáo không hợp lệ từ khách hàng (Ví dụ: Báo mất thẻ ảo).
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Nhận lý do từ RequestParam `reason`.
     * 2. Gọi `incidentService.rejectIncident(id, reason)`.
     * 3. Trả về HTTP 200 OK.
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> rejectIncident(
            @PathVariable Long id,
            @RequestParam String reason) {
        try {
            IncidentTicketDTO dto = incidentService.rejectIncident(id, reason);
            return ResponseEntity.ok(ApiResponse.success(dto, "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 10: XỬ LÝ SỰ CỐ KHÔNG DÙNG THẺ (RESOLVE NON-CARD)
     * =========================================================================
     * MỤC ĐÍCH: Xử lý đóng các sự cố không dính dáng tới thẻ từ (VD: xe vô đỗ không
     * đăng ký).
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Đọc ghi chú và ảnh minh chứng.
     * 2. Gọi `incidentService.resolveNonCardIncident(id, resolutionNotes, docUrl)`.
     * 3. Trả về HTTP 200 OK.
     */
    @PutMapping("/{id}/resolve-non-card")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> resolveNonCard(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String resolutionNotes = body.get("resolutionNotes");
            String docUrl = body.get("resolutionImageUrl");
            IncidentTicketDTO dto = incidentService.resolveNonCardIncident(id, resolutionNotes, docUrl);
            return ResponseEntity.ok(ApiResponse.success(dto, "Resolved non-card incident successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 11: KIỂM TRA TRẠNG THÁI BIỂN SỐ TRONG BÃI (CHECK PLATE ACTIVE)
     * =========================================================================
     * MỤC ĐÍCH: Xác minh xem biển số xe này có đang đỗ trong bãi không trước khi
     * cho tạo sự cố.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Nhận biển số (`plate`) và loại xe (`vehicleTypeId`).
     * 2. Gọi `incidentService.checkPlateActiveInfo(plate, vehicleTypeId)`.
     * 3. Trả về HTTP 200 OK chứa thông tin phiên đỗ xe (nếu có).
     */
    @GetMapping("/check-plate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPlateActive(
            @RequestParam String plate,
            @RequestParam(required = false) Long vehicleTypeId) {
        return ResponseEntity.ok(
                ApiResponse.success(incidentService.checkPlateActiveInfo(plate, vehicleTypeId), "Check the status"));
    }

    /**
     * =========================================================================
     * API 12: KIỂM TRA ĐỐI CHIẾU BIỂN SỐ VÀ THẺ RFID (CHECK PLATE AND RFID)
     * =========================================================================
     * MỤC ĐÍCH: Xác minh chéo xem biển số xe X và thẻ từ Y có đang khớp nhau trong
     * bãi không.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Nhận `plate`, `rfid`, `vehicleTypeId`.
     * 2. Gọi `incidentService.checkPlateAndRfidActiveInfo(...)`.
     * 3. Trả về kết quả khớp hay không.
     */
    @GetMapping("/check-plate-rfid")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPlateAndRfidActive(
            @RequestParam String plate,
            @RequestParam String rfid,
            @RequestParam(required = false) Long vehicleTypeId) {
        return ResponseEntity.ok(ApiResponse
                .success(incidentService.checkPlateAndRfidActiveInfo(plate, rfid, vehicleTypeId), "Check the status"));
    }

    /**
     * =========================================================================
     * API 13: BÁO MẤT THẺ KHẨN CẤP (REPORT LOST CARD)
     * =========================================================================
     * MỤC ĐÍCH: Khách hàng/Nhân viên tạo nhanh yêu cầu báo mất thẻ từ.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Parse thông tin biển số, mức phí đền, mô tả, ảnh chụp giấy tờ (Gương
     * mặt/CCCD).
     * 2. Lấy email người gửi từ Authentication.
     * 3. Gọi `incidentService.createLostCardIncident(...)`.
     * 4. Trả về HTTP 200 OK kèm Ticket báo mất thẻ.
     */
    @PostMapping("/lost-card")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> reportLostCard(
            @RequestBody Map<String, Object> requestBody,
            Authentication authentication) {
        try {
            String email = authentication != null ? authentication.getName() : null;
            String plate = (String) requestBody.get("plate");
            BigDecimal fee = requestBody.get("fee") != null
                    ? new BigDecimal(requestBody.get("fee").toString())
                    : null;
            String description = (String) requestBody.get("description");
            String uploadedDocUrl = (String) requestBody.get("uploadedDocUrl");
            Long vehicleTypeId = requestBody.get("vehicleTypeId") != null
                    ? Long.parseLong(requestBody.get("vehicleTypeId").toString())
                    : null;
            IncidentTicketDTO dto = incidentService.createLostCardIncident(plate, fee, description, uploadedDocUrl,
                    email, vehicleTypeId);
            return ResponseEntity.ok(ApiResponse.success(dto, "Reported lost card successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 14: DÀNH CHO QUẢN LÝ - ĐIỀU CHỈNH GIẢM GIÁ KHIẾU NẠI (ADJUST FEE DISPUTE)
     * =========================================================================
     * MỤC ĐÍCH: Chỉ có Quản lý (ROLE_MANAGER) mới có quyền duyệt mức giảm giá khiếu
     * nại phí.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Bảo mật: Yêu cầu `@PreAuthorize("hasRole('MANAGER')")`.
     * 2. Parse `discountAmount`, ghi chú và ảnh quyết định giảm giá.
     * 3. Gọi `incidentService.resolveFeeDispute(...)`.
     * 4. Trả về HTTP 200 OK.
     */
    @PutMapping("/{id}/adjust-fee-dispute")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<Boolean>> adjustFeeDispute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        try {
            BigDecimal discountAmount = new BigDecimal(requestBody.get("discountAmount").toString());
            String resolutionNotes = (String) requestBody.get("resolutionNotes");
            String resolutionImageUrl = (String) requestBody.get("resolutionImageUrl");

            incidentService.resolveFeeDispute(id, discountAmount, resolutionNotes, resolutionImageUrl);
            return ResponseEntity.ok(ApiResponse.success(true, "Fee dispute adjusted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    /**
     * =========================================================================
     * API 15: ĐIỀU CHỈNH PHÍ TRỰC TIẾP (ADJUST FEE)
     * =========================================================================
     * MỤC ĐÍCH: Tạo sự cố điều chỉnh phí trực tiếp khi số tiền trên hệ thống bị sai
     * lệch.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Parse thông tin biển số, số tiền phí thực tế (`liveFee`), lý do điều
     * chỉnh.
     * 2. Gọi `incidentService.adjustFeeIncident(...)`.
     * 3. Trả về HTTP 200 OK.
     */
    @PostMapping("/adjust-fee")
    public ResponseEntity<ApiResponse<IncidentTicketDTO>> adjustFee(
            @RequestBody Map<String, Object> requestBody) {
        try {
            String plate = (String) requestBody.get("plate");
            BigDecimal liveFee = new BigDecimal(requestBody.get("liveFee").toString());
            String reason = (String) requestBody.get("reason");
            Object vtIdObj = requestBody.get("vehicleTypeId");
            Long vehicleTypeId = vtIdObj != null ? Long.valueOf(vtIdObj.toString()) : null;
            IncidentTicketDTO dto = incidentService.adjustFeeIncident(plate, liveFee, reason, vehicleTypeId);
            return ResponseEntity.ok(ApiResponse.success(dto, "Fee adjusted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
}
