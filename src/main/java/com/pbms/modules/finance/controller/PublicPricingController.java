package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.finance.dto.PricingPolicyDTO;
import com.pbms.modules.finance.service.PricingConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/pricing")
public class PublicPricingController {

    private final PricingConfigurationService pricingConfigurationService;

    public PublicPricingController(PricingConfigurationService pricingConfigurationService) {
        this.pricingConfigurationService = pricingConfigurationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingPolicyDTO>>> getAllPolicies() {
        return ResponseEntity.ok(ApiResponse.success(pricingConfigurationService.getAllPolicies(), "Fetched public pricing policies"));
    }
}

