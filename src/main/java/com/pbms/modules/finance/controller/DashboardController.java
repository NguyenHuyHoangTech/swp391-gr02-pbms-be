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
     * [BE_FN_001] Lấy dữ liệu tổng quan vận hành (Operational Overview).
     * - API Endpoint: GET /api/v1/finance/dashboard/operational?date=YYYY-MM-DD
     * - Chức năng: Cung cấp dữ liệu check-ins, check-outs, sức chứa bãi xe (live capacity) phân theo Walk-in Zone, Booking, Monthly Zone của từng loại phương tiện.
     * 
     * @param date Ngày cần xem (mặc định là ngày hiện tại).
     * @return Các chỉ số vận hành tổng quan.
     */
    @GetMapping("/operational")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOperationalOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Map<String, Object> data = dashboardService.getOperationalOverview(date);
        return ResponseEntity.ok(ApiResponse.success(data, "Operational overview retrieved successfully"));
    }

    /**
     * [BE_FN_002] Lấy lưu lượng xe vào/ra theo giờ (Hourly Traffic Flow).
     * - API Endpoint: GET /api/v1/finance/dashboard/hourly-flow?date=YYYY-MM-DD
     * - Chức năng: Thống kê số xe check-in và check-out theo 24 khung giờ trong ngày để vẽ biểu đồ lưu lượng giao thông.
     * 
     * @param date Ngày cần thống kê lưu lượng.
     * @return Danh sách lưu lượng theo từng giờ.
     */
    @GetMapping("/hourly-flow")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHourlyFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<Map<String, Object>> data = dashboardService.getHourlyFlow(date);
        return ResponseEntity.ok(ApiResponse.success(data, "Hourly flow retrieved successfully."));
    }

}

