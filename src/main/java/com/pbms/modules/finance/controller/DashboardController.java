package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.finance.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;


    /**
     * GET /api/v1/finance/dashboard/operational?date=YYYY-MM-DD
     * API chính cung cấp dữ liệu tổng quan cho Báo cáo Vận hành (Operational Dashboard).
     * Trả về các chỉ số: Total Check-ins, Total Check-outs, Peak Hour và đặc biệt là
     * Live Capacity (Sức chứa hiện tại của bãi xe chia theo Walk-in Zone và Monthly Zone).
     */
    @GetMapping("/operational")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOperationalOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Map<String, Object> data = dashboardService.getOperationalOverview(date);
        return ResponseEntity.ok(ApiResponse.success(data, "Operational overview retrieved successfully"));
    }

    /**
     * GET /api/v1/finance/dashboard/hourly-flow?date=YYYY-MM-DD
     * Trả về dữ liệu lưu lượng xe vào/ra (Check-ins/Check-outs) theo từng khung giờ trong ngày.
     * Dữ liệu này được dùng để vẽ biểu đồ "Hourly Traffic Flow", giúp người quản lý
     * nhận biết các khung giờ cao điểm để điều phối nhân sự trực cổng.
     */
    @GetMapping("/hourly-flow")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHourlyFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<Map<String, Object>> data = dashboardService.getHourlyFlow(date);
        return ResponseEntity.ok(ApiResponse.success(data, "Hourly flow retrieved successfully."));
    }

}

