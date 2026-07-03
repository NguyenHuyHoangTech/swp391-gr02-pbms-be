package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.finance.dto.RevenueRecordDTO;
import com.pbms.modules.finance.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService revenueService;

    /**
     * GET /api/v1/revenue/dashboard?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
     * Tráº£ vá» Master Dataset pháº³ng phá»¥c vá»¥ cho mÃ n hÃ¬nh Revenue Dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<List<RevenueRecordDTO>>> getRevenueDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<RevenueRecordDTO> data = revenueService.getRevenueDashboardData(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(data, "This is the most effective way to report monthly revenue."));
    }
}

