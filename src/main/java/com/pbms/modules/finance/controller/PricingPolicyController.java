package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.finance.dto.PricingPolicyDTO;
import com.pbms.modules.finance.service.PricingConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import com.pbms.common.annotation.LogAudit;

@RestController
@RequestMapping("/api/v1/finance/pricing-policies")
@RequiredArgsConstructor
@Slf4j
public class PricingPolicyController {

    private final PricingConfigurationService pricingConfigurationService;



    /**
     * Lấy danh sách toàn bộ các chính sách giá (Pricing Policy) hiện tại của hệ thống.
     * Chỉ những người dùng có quyền ADMIN hoặc MANAGER mới được phép truy xuất thông tin này.
     *
     * @return Danh sách các chính sách giá kèm theo các ca (Shifts) và khối thời gian (Blocks).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PricingPolicyDTO>>> getAllPolicies() {
        return ResponseEntity.ok(ApiResponse.success(pricingConfigurationService.getAllPolicies(), "It's so easy to get"));
    }

    /**
     * Tạo mới hoặc cập nhật một chính sách giá cho một loại xe cụ thể.
     * Hệ thống sẽ xóa (ghi đè) cấu hình cũ của loại xe đó và áp dụng cấu hình mới.
     * Hành động này sẽ được ghi log Audit để theo dõi sự thay đổi.
     *
     * @param dto Đối tượng DTO chứa thông tin loại xe, global base, các ca và blocks.
     * @return Chính sách giá đã được lưu thành công vào CSDL.
     */
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

    /**
     * Mô phỏng thuật toán tính phí gửi xe (Dùng cho Admin/Manager test thử cấu hình).
     * Sẽ tạo một bảng giá ảo (transient policy) trên bộ nhớ (RAM) và giả lập một khoảng thời gian
     * đậu xe (timeIn -> timeOut) để kiểm tra xem hệ thống tính toán ra bao nhiêu tiền, mà không cần
     * phải lưu bảng giá đó xuống Database.
     *
     * @param request Đầu vào chứa bảng giá mô phỏng và mốc thời gian đậu xe.
     * @return Chi tiết phép tính (Breakdown Trace) và tổng số tiền.
     */
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

