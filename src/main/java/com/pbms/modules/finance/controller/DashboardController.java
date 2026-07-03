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
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRevenueOverview(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        
        Map<String, Object> data = dashboardService.getRevenueOverview(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(data, "The amount of revenue is the same"));
    }

    @GetMapping("/operational")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOperationalOverview() {
        
        Map<String, Object> data = dashboardService.getOperationalOverview();
        return ResponseEntity.ok(ApiResponse.success(data, "How many times do you have to deal with this problem?"));
    }

    /**
     * GET /api/v1/dashboard/occupancy?date=YYYY-MM-DD
     * Tráº£ vá» dá»¯ liá»‡u lÆ°á»£ng xe trong bÃ£i theo tá»«ng giá» trong ngÃ y (biá»ƒu Ä‘á»“ dÃ¢ng nÆ°á»›c)
     */
    @GetMapping("/occupancy")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHourlyOccupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<Map<String, Object>> data = dashboardService.getHourlyOccupancy(date);
        return ResponseEntity.ok(ApiResponse.success(data, "Success"));
    }

    /**
     * GET /api/v1/dashboard/hourly-flow?date=YYYY-MM-DD
     * Tráº£ vá» dá»¯ liá»‡u lÆ°u lÆ°á»£ng vÃ o/ra giá» cao Ä‘iá»ƒm (Khu vá»±c 4)
     */
    @GetMapping("/hourly-flow")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHourlyFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<Map<String, Object>> data = dashboardService.getHourlyFlow(date);
        return ResponseEntity.ok(ApiResponse.success(data, "The price of this product is as high as the price."));
    }

    /**
     * GET /api/v1/dashboard/macro-trends?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
     * Tráº£ vá» tá»• há»£p dá»¯ liá»‡u phÃ¢n tÃ­ch vÄ© mÃ´ (Khu vá»±c 5)
     */
    @GetMapping("/macro-trends")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMacroTrends(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String category) {
        
        Map<String, Object> data = dashboardService.getMacroTrends(startDate, endDate, category);
        return ResponseEntity.ok(ApiResponse.success(data, "This is a great solution to the problem."));
    }
}

