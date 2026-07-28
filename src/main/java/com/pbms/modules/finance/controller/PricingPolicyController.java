package com.pbms.modules.finance.controller;

// Thư mục DTO chung dùng để chuẩn hóa dữ liệu trả về từ API (chứa mã lỗi, thông báo, data)
import com.pbms.common.dto.ApiResponse;
// Data Transfer Object chứa cấu trúc dữ liệu gửi và nhận liên quan đến chính sách giá (Pricing Policy)
import com.pbms.modules.finance.dto.PricingPolicyDTO;
// Service quản lý cấu hình giá gửi nhận dữ liệu và chuyển đổi giữa Entity và DTO
import com.pbms.modules.finance.service.PricingConfigurationService;
// Lớp đóng gói HTTP status code, headers và body trong Spring MVC
import org.springframework.http.ResponseEntity;
// Annotation hỗ trợ phân quyền ở mức phương thức (Method-level Security) trước khi gọi hàm
import org.springframework.security.access.prepost.PreAuthorize;
// Nhập toàn bộ các annotation phục vụ viết REST Endpoint (RestController, RequestMapping, GetMapping,...)
import org.springframework.web.bind.annotation.*;
// Lombok annotation tự động tạo constructor cho các thuộc tính final
import lombok.RequiredArgsConstructor;
// Lombok annotation cung cấp logger (SLF4J) tự động để ghi log gỡ lỗi
import lombok.extern.slf4j.Slf4j;

// Giao diện List đại diện cho tập hợp phần tử có thứ tự trong Java
import java.util.List;
// Custom annotation hỗ trợ ghi lại lịch sử thao tác của người dùng (Audit Log)
import com.pbms.common.annotation.LogAudit;

/**
 * =========================================================================================
 * 🌟 BỨC TRANH TOÀN CẢNH CỤ THỂ CHUẨN KỸ THUẬT CỦA PricingPolicyController.java 🌟
 * =========================================================================================
 * 
 * 1. AI KHỞI TẠO NÓ LÊN? (VÒNG ĐỜI - LIFECYCLE)
 * - Khi Spring Boot quét classpath, nó sẽ tạo thực thể Singleton Bean cho `PricingPolicyController` do có tag `@RestController`.
 * - Spring sẽ tự động bơm (Inject) `PricingConfigurationService` vào constructor của class này để sử dụng các hàm quản lý cấu hình bảng giá.
 * 
 * 2. AI GỌI ĐẾN NÓ? (ĐẦU VÀO CỤ THỂ - INPUT)
 * Web Frontend (màn hình PricingConfigScreen.tsx) hoặc Postman sẽ gửi các HTTP Request cụ thể tới các URL sau (đã ghép tiền tố `/api/v1/finance/pricing-policies`):
 * - GET  `/`              : (Yêu cầu vai trò ADMIN hoặc MANAGER) Trả về toàn bộ danh sách chính sách giá hiện tại trong hệ thống.
 * - POST `/`              : Mang theo body JSON (PricingPolicyDTO) để lưu hoặc cập nhật một cấu hình bảng giá hoàn chỉnh cho loại xe cụ thể.
 * - POST `/test-calculate`: Mang theo body JSON (TestCalculateRequestDTO) chứa cấu hình bảng giá tạm thời cùng mốc giờ vào - ra để giả lập tính tiền gửi xe thử nghiệm trực tiếp trên giao diện thiết lập.
 * 
 * 3. NÓ GỌI ĐẾN AI? (ĐẦU RA CỤ THỂ TỚI SERVICE VÀ DATABASE)
 * Lớp Controller này lập tức phân phối công việc cho dịch vụ cấu hình giá và tính toán thử nghiệm:
 * 
 * - Hàm `getAllPolicies()`:
 *   -> GỌI: `pricingConfigurationService.getAllPolicies()`
 *   -> Hậu quả: Đọc cơ sở dữ liệu để lấy toàn bộ chính sách giá hoạt động và map sang DTO.
 * 
 * - Hàm `savePolicy()`:
 *   -> GỌI: `pricingConfigurationService.savePolicy(dto)`
 *   -> Hậu quả: Xóa sạch các ca cũ (`shifts`) và các block cũ (`blocks`) liên quan đến chính sách của loại xe này và lưu lại thông tin mới vào bảng `pricing_policies`, `pricing_shifts`, `pricing_blocks` trong DB.
 * 
 * - Hàm `testCalculateFee()`:
 *   -> GỌI 1: `pricingConfigurationService.createTransientPolicy(request.getPolicy())` để tạo thực thể chính sách giả định không lưu xuống CSDL.
 *   -> GỌI 2: `calculatorService.calculateWithTrace(policy, timeIn, timeOut)` để tính toán ra tổng tiền thực tế kèm lịch trình chi tiết (Breakdown Trace) cho người dùng đối soát trên Frontend.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/finance/pricing-policies")
@RequiredArgsConstructor
@Slf4j
public class PricingPolicyController {

    private final PricingConfigurationService pricingConfigurationService;



    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PricingPolicyDTO>>> getAllPolicies() {
        return ResponseEntity.ok(ApiResponse.success(pricingConfigurationService.getAllPolicies(), "It's so easy to get"));
    }

    @PostMapping
    // @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @LogAudit(action = "UPDATE", resource = "PricingPolicy", description = "Update pricing configuration")
    public ResponseEntity<ApiResponse<PricingPolicyDTO>> savePolicy(@RequestBody PricingPolicyDTO dto) {
        try {
            log.info("====== RECEIVED PAYLOAD ======");
            log.info("VehicleTypeId: {}", dto.getVehicleTypeId());
            log.info("Shifts count: {}", (dto.getShifts() != null ? dto.getShifts().size() : "null"));
            log.info("==============================");
            PricingPolicyDTO saved = pricingConfigurationService.savePolicy(dto);
            log.info("====== SAVED SUCCESSFULLY ======");
            return ResponseEntity.ok(ApiResponse.success(saved, "The price of the cake is the same."));
        } catch (Exception e) {
            log.error("====== ERROR: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/test-calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<com.pbms.modules.finance.dto.CalculationResultDTO>> testCalculateFee(@RequestBody com.pbms.modules.finance.dto.TestCalculateRequestDTO request) {
        try {
            com.pbms.modules.finance.domain.PricingPolicy policy = pricingConfigurationService.createTransientPolicy(request.getPolicy());
            com.pbms.modules.finance.service.PricingCalculatorService calculatorService = 
                new com.pbms.modules.finance.service.PricingCalculatorService(null); // No repo needed for test
            
            com.pbms.modules.finance.dto.CalculationResultDTO result = calculatorService.calculateWithTrace(
                policy, 
                request.getTimeIn(), 
                request.getTimeOut()
            );
            return ResponseEntity.ok(ApiResponse.success(result, "Calculated"));
        } catch (Exception e) {
            log.error("Error calculating test fee: ", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error calculating fee: " + e.getMessage()));
        }
    }
}

