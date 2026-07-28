package com.pbms.modules.finance.controller;

// Thư mục DTO chung dùng để chuẩn hóa dữ liệu trả về từ API (chứa mã lỗi, thông báo, data)
import com.pbms.common.dto.ApiResponse;
// Data Transfer Object đại diện cho một bản ghi doanh thu tổng hợp (Revenue Record)
import com.pbms.modules.finance.dto.RevenueRecordDTO;
// Service xử lý logic nghiệp vụ và truy xuất dữ liệu doanh thu (Revenue) từ Database
import com.pbms.modules.finance.service.RevenueService;
// Annotation của Lombok giúp tự động sinh constructor cho các biến final
import lombok.RequiredArgsConstructor;
// Định dạng ngày giờ đầu vào từ request param dưới dạng ISO (ví dụ: YYYY-MM-DD)
import org.springframework.format.annotation.DateTimeFormat;
// Lớp đóng gói HTTP status code, headers và body trong Spring MVC
import org.springframework.http.ResponseEntity;
// Lớp quản lý các tiêu đề HTTP (Headers), ví dụ thiết lập Content-Disposition để tải tệp
import org.springframework.http.HttpHeaders;
// Đại diện cho kiểu phương tiện HTTP (MIME Type), ví dụ text/csv, application/json
import org.springframework.http.MediaType;
// Nhập tất cả các annotation liên quan đến Spring REST web (GetMapping, RequestParam, RestController...)
import org.springframework.web.bind.annotation.*;
// Interface của Spring MVC hỗ trợ ghi trực tiếp dữ liệu ra Output Stream (hữu ích cho tải tệp lớn/streaming CSV)
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

// Đối tượng biểu diễn ngày trong Java (Java 8 Date & Time API)
import java.time.LocalDate;
// Đại diện cho kiểu danh sách trong Java
import java.util.List;

/**
 * =========================================================================================
 * 🌟 BỨC TRANH TOÀN CẢNH CỤ THỂ CHUẨN KỸ THUẬT CỦA RevenueController.java 🌟
 * =========================================================================================
 * 
 * 1. AI KHỞI TẠO NÓ LÊN? (VÒNG ĐỜI - LIFECYCLE)
 * - Khi ứng dụng Spring Boot khởi động, bộ quét thành phần Component Scan sẽ phát hiện class này nhờ tag `@RestController`.
 * - Spring IoC Container tự động tạo một thực thể Singleton duy nhất (Singleton Bean) của Controller này.
 * - Container sẽ tự động tiêm (Inject) thực thể `RevenueService` vào constructor thông qua cơ chế Dependency Injection (nhờ `@RequiredArgsConstructor`).
 * 
 * 2. AI GỌI ĐẾN NÓ? (ĐẦU VÀO CỤ THỂ - INPUT)
 * Web Frontend (màn hình RevenueDashboardScreen.tsx) gửi các yêu cầu HTTP Request đến các API được định nghĩa ở đây:
 * - GET `.../api/v1/finance/revenue/dashboard`: Yêu cầu các tham số `startDate` và `endDate` để lấy dữ liệu biểu đồ.
 * - GET `.../api/v1/finance/revenue/table`: Yêu cầu `startDate`, `endDate`, và các tham số phân trang (`page`, `size`) để điền dữ liệu bảng chi tiết.
 * - GET `.../api/v1/finance/revenue/export`: Yêu cầu `startDate` và `endDate` để tải về tệp tin báo cáo CSV.
 * 
 * 3. NÓ GỌI ĐẾN AI? (ĐẦU RA CỤ THỂ TỚI SERVICE VÀ DATABASE)
 * Lớp này đóng vai trò điều phối luồng (Controller), ngay khi nhận request nó sẽ chuyển giao công việc cho:
 * 
 * - Hàm `getRevenueDashboard()`:
 *   -> GỌI: `revenueService.getRevenueDashboardData(startDate, endDate)`
 *   -> Mục đích: Thực thi truy vấn SQL gộp dữ liệu doanh thu của các lượt đỗ xe (Standard, Reservation, Monthly) và các phí phạt trong khoảng ngày yêu cầu.
 * 
 * - Hàm `getRevenueTable()`:
 *   -> GỌI: `revenueService.getRevenueTableData(startDate, endDate, page, size)`
 *   -> Mục đích: Phân trang danh sách giao dịch doanh thu ở mức cơ sở dữ liệu để tối ưu dung lượng RAM và tốc độ tải trang.
 * 
 * - Hàm `exportRevenue()`:
 *   -> GỌI: `revenueService.exportRevenueCsv(startDate, endDate)`
 *   -> Mục đích: Tạo một luồng ghi trực tiếp nội dung CSV (Streaming Response) trả về trình duyệt giúp tránh tràn bộ nhớ khi tải lượng dữ liệu khổng lồ.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/finance/revenue")
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
        if (startDate != null && endDate != null && java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 90) {
            throw new IllegalArgumentException("Khoảng thời gian xem biểu đồ Doanh thu không được vượt quá 90 ngày (3 tháng). Vui lòng dùng tính năng Export CSV nếu cần tải dữ liệu dài hạn.");
        }

        List<RevenueRecordDTO> data = revenueService.getRevenueDashboardData(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(data, "This is the most effective way to report monthly revenue."));
    }

    @GetMapping("/table")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.pbms.modules.finance.dto.RevenueTransactionDTO>>> getRevenueTable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        org.springframework.data.domain.Page<com.pbms.modules.finance.dto.RevenueTransactionDTO> data = revenueService.getRevenueTableData(startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(data, "Paginated revenue data retrieved successfully."));
    }

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

