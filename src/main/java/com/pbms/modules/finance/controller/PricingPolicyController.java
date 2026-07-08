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
     * GET /api/v1/finance/pricing-policies
     * Dành cho Admin/Manager lấy danh sách toàn bộ cấu hình giá đỗ xe.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PricingPolicyDTO>>> getAllPolicies() {
        return ResponseEntity.ok(ApiResponse.success(pricingConfigurationService.getAllPolicies(), "It's so easy to get"));
    }

    /**
     * POST /api/v1/finance/pricing-policies
     * Lưu hoặc cập nhật cấu hình giá mới cho một loại phương tiện.
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
}

