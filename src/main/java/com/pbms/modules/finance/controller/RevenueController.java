package com.pbms.modules.finance.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.finance.dto.RevenueRecordDTO;
import com.pbms.modules.finance.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finance/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService revenueService;

    /**
     * GET /api/v1/finance/revenue/dashboard
     * Lấy dữ liệu tổng quan để hiển thị biểu đồ Doanh thu (Revenue Dashboard).
     * Kết quả trả về là một danh sách tổng hợp theo từng ngày, loại xe, nguồn thu.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<List<RevenueRecordDTO>>> getRevenueDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        // [BỔ SUNG MỚI]: Giới hạn khoảng thời gian truy vấn tối đa 90 ngày để tránh quá tải server
        if (startDate != null && endDate != null && java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 90) {
            throw new IllegalArgumentException("Khoảng thời gian xem biểu đồ Doanh thu không được vượt quá 90 ngày (3 tháng). Vui lòng dùng tính năng Export CSV nếu cần tải dữ liệu dài hạn.");
        }

        List<RevenueRecordDTO> data = revenueService.getRevenueDashboardData(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(data, "This is the most effective way to report monthly revenue."));
    }

    /**
     * GET /api/v1/finance/revenue/table
     * Lấy dữ liệu doanh thu có phân trang (Pagination) để hiển thị lên bảng dữ liệu (Data Table).
     * Tính năng phân trang giúp trình duyệt không bị treo khi dữ liệu quá lớn.
     */
    @GetMapping("/table")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<RevenueRecordDTO>>> getRevenueTable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        org.springframework.data.domain.Page<RevenueRecordDTO> data = revenueService.getRevenueTableData(startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Paginated revenue data retrieved successfully."));
    }

    /**
     * GET /api/v1/finance/revenue/export
     * Xuất toàn bộ dữ liệu doanh thu trong khoảng thời gian đã chọn thành file CSV.
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        StreamingResponseBody stream = revenueService.exportRevenueCsv(startDate, endDate);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"revenue_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(stream);
    }
}
