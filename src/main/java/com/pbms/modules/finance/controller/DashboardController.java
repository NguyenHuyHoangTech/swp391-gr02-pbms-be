package com.pbms.modules.finance.controller;

// Thư mục DTO chung dùng để chuẩn hóa dữ liệu trả về từ API (chứa mã lỗi, thông báo, data)
import com.pbms.common.dto.ApiResponse;
// Service xử lý nghiệp vụ liên quan đến việc tính toán, truy xuất dữ liệu cho Dashboard
import com.pbms.modules.finance.service.DashboardService;
// Annotation của Lombok tự động tạo Constructor với các tham số bắt buộc (các trường final)
import lombok.RequiredArgsConstructor;
// Annotation hỗ trợ định dạng ngày tháng (LocalDate/LocalDateTime) truyền qua Request Parameter
import org.springframework.format.annotation.DateTimeFormat;
// Lớp đại diện cho một phản hồi HTTP hoàn chỉnh, chứa headers, body và HTTP status code
import org.springframework.http.ResponseEntity;
// Annotation đánh dấu hàm xử lý HTTP GET request
import org.springframework.web.bind.annotation.GetMapping;
// Annotation ánh xạ URL path chung cho toàn bộ Controller này (/api/v1/finance/dashboard)
import org.springframework.web.bind.annotation.RequestMapping;
// Annotation dùng để trích xuất các tham số từ Query String trong URL (Query Parameters)
import org.springframework.web.bind.annotation.RequestParam;
// Annotation xác định đây là Rest Controller của Spring, trả về JSON/XML thay vì render view
import org.springframework.web.bind.annotation.RestController;

// Thư viện chuẩn Java đại diện cho đối tượng ngày (không chứa thông tin giờ, múi giờ)
import java.time.LocalDate;
// Thư viện danh sách (List) trong Java
import java.util.List;
// Thư viện ánh xạ khóa-giá trị (Map) để lưu trữ dữ liệu dạng cặp key-value
import java.util.Map;

/**
 * =========================================================================================
 * 🌟 BỨC TRANH TOÀN CẢNH CỤ THỂ CHUẨN KỸ THUẬT CỦA DashboardController.java 🌟
 * =========================================================================================
 * 
 * 1. AI KHỞI TẠO NÓ LÊN? (VÒNG ĐỜI - LIFECYCLE)
 * - Khi ứng dụng Spring Boot chạy, bộ quét annotation sẽ tìm thấy lớp này nhờ tag `@RestController`.
 * - Hệ thống sẽ khởi tạo một Singleton Bean cho lớp này trong IoC Container.
 * - Lớp `DashboardService` sẽ được tự động tiêm vào thông qua Constructor nhờ Lombok `@RequiredArgsConstructor`.
 * 
 * 2. AI GỌI ĐẾN NÓ? (ĐẦU VÀO CỤ THỂ - INPUT)
 * Web Frontend (Màn hình OperationalDashboardScreen.tsx) sẽ gửi các yêu cầu HTTP GET định kỳ (mỗi 5 giây) để cập nhật dữ liệu vận hành thời gian thực:
 * - GET `.../api/v1/finance/dashboard/operational?date=YYYY-MM-DD`: Nhận tham số ngày để tính tổng kiểm soát chỗ đỗ và xe ra/vào.
 * - GET `.../api/v1/finance/dashboard/hourly-flow?date=YYYY-MM-DD`: Nhận tham số ngày để vẽ sơ đồ lưu lượng xe vào/ra theo khung giờ.
 * 
 * 3. NÓ GỌI ĐẾN AI? (ĐẦU RA CỤ THỂ TỚI SERVICE VÀ DATABASE)
 * Hướng đi tiếp theo của dữ liệu là đi sâu vào lớp Service và thực hiện các phép gom nhóm SQL thô thông qua JDBC:
 * 
 * - Hàm `getOperationalOverview()`:
 *   -> GỌI: `dashboardService.getOperationalOverview(date)`
 *   -> Mục đích: Tính toán công suất đỗ xe thời gian thực (Live Capacity) chia theo Walk-in Zone và Monthly Zone, cùng tổng check-in/check-out ngày hôm đó.
 * 
 * - Hàm `getHourlyFlow()`:
 *   -> GỌI: `dashboardService.getHourlyFlow(date)`
 *   -> Mục đích: Tổng hợp số lượng xe vào và ra ở mỗi 24 khung giờ riêng biệt cho từng loại xe để vẽ biểu đồ đường (Line Chart).
 * =========================================================================================
 */
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

