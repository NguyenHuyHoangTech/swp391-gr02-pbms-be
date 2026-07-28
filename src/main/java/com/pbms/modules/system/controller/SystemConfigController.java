package com.pbms.modules.system.controller;

// Thư mục DTO chung dùng để chuẩn hóa dữ liệu trả về từ API (chứa mã lỗi, thông báo, data)
import com.pbms.common.dto.ApiResponse;
// Entity đại diện cho cấu hình hệ thống (gồm khóa key và giá trị value lưu trong DB)
import com.pbms.modules.system.domain.SystemConfig;
// Service quản lý việc truy vấn, chỉnh sửa các cấu hình hệ thống
import com.pbms.modules.system.service.SystemConfigService;
// Lớp đóng gói HTTP status code, headers và body trong Spring MVC
import org.springframework.http.ResponseEntity;
// Annotation hỗ trợ phân quyền ở mức phương thức (Method-level Security) trước khi gọi hàm
import org.springframework.security.access.prepost.PreAuthorize;
// Nhập toàn bộ các annotation phục vụ viết REST Endpoint (RestController, RequestMapping, GetMapping,...)
import org.springframework.web.bind.annotation.*;

// Giao diện List đại diện cho tập hợp phần tử có thứ tự trong Java
import java.util.List;
// Custom annotation hỗ trợ ghi lại lịch sử thao tác của người dùng (Audit Log)
import com.pbms.common.annotation.LogAudit;

/**
 * =========================================================================================
 * 🌟 BỨC TRANH TOÀN CẢNH CỤ THỂ CHUẨN KỸ THUẬT CỦA SystemConfigController.java 🌟
 * =========================================================================================
 * 
 * 1. AI KHỞI TẠO NÓ LÊN? (VÒNG ĐỜI - LIFECYCLE)
 * - Khi Spring Boot được chạy, IoC Container sẽ tự động quét qua file này thông qua annotation `@RestController`.
 * - Lớp này được khởi tạo dưới dạng một thực thể Singleton duy nhất (Singleton Bean) để hứng mọi request cấu hình.
 * - Lớp `SystemConfigService` sẽ được tự động bơm (Inject) qua Constructor để liên kết nghiệp vụ lấy, ghi cấu hình.
 * 
 * 2. AI GỌI ĐẾN NÓ? (ĐẦU VÀO CỤ THỂ - INPUT)
 * Web Frontend (màn hình PenaltyConfigScreen.tsx) hoặc các tác vụ quản trị hệ thống sẽ gửi các HTTP Request cụ thể:
 * - GET  `.../api/v1/system/configs`     : Lấy toàn bộ các cấu hình hệ thống bao gồm cả các phí phạt sự cố.
 * - GET  `.../api/v1/system/configs/{key}`: Truy vấn cấu hình cụ thể qua khóa định danh (Ví dụ: `PENALTY_LOST_CARD`).
 * - POST `.../api/v1/system/configs`     : Thêm mới một cấu hình hệ thống mới.
 * - PUT  `.../api/v1/system/configs/{id}`: Cập nhật giá trị cấu hình phạt (Ví dụ: Đổi giá trị phạt đỗ sai Zone từ 50,000đ thành 100,000đ).
 * - DELETE `.../api/v1/system/configs/{id}`: Xóa bỏ một cấu hình ra khỏi cơ sở dữ liệu.
 * 
 * 3. NÓ GỌI ĐẾN AI? (ĐẦU RA CỤ THỂ TỚI SERVICE VÀ DATABASE)
 * Lớp này lập tức chuyển giao gói tin tới lớp service xử lý cấu hình:
 * 
 * - Hàm `getAllConfigs()`:
 *   -> GỌI: `service.getAllConfigs()`
 *   -> Hậu quả: Đọc CSDL để trả về toàn bộ danh sách cấu hình.
 * 
 * - Hàm `updateConfig()`:
 *   -> GỌI: `service.updateConfig(id, configDetails)`
 *   -> Hậu quả: Cập nhật dữ liệu mới vào bảng `system_configs` trong DB, đồng thời làm mới bộ nhớ đệm (Evict/Update Cache) để đảm bảo các cổng soát vé xe áp dụng phí phạt mới ngay lập tức mà không cần khởi động lại Server.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/system/configs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'STAFF')")
public class SystemConfigController {

    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfig>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(service.getAllConfigs(), "Configs fetched successfully"));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfig>> getConfigByKey(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(service.getConfigByKey(key), "Config fetched successfully"));
    }

    @PostMapping
    @LogAudit(action = "CREATE", resource = "System/PenaltyConfig", description = "Create new system or penalty configuration")
    public ResponseEntity<ApiResponse<SystemConfig>> createConfig(@RequestBody SystemConfig config) {
        return ResponseEntity.ok(ApiResponse.success(service.createConfig(config), "Config created successfully"));
    }

    @PutMapping("/{id}")
    @LogAudit(action = "UPDATE", resource = "System/PenaltyConfig", description = "Update system or penalty configuration")
    public ResponseEntity<ApiResponse<SystemConfig>> updateConfig(@PathVariable Long id, @RequestBody SystemConfig configDetails) {
        return ResponseEntity.ok(ApiResponse.success(service.updateConfig(id, configDetails), "Config updated successfully"));
    }

    @DeleteMapping("/{id}")
    @LogAudit(action = "DELETE", resource = "System/PenaltyConfig", description = "Delete system or penalty configuration")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable Long id) {
        service.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Config deleted successfully"));
    }

    @PostMapping("/test-email")
    public ResponseEntity<ApiResponse<String>> testEmail(@RequestBody java.util.Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");
        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Email and password are required"));
        }
        service.testSmtpConnection(email, password);
        return ResponseEntity.ok(ApiResponse.success("Connection successful", "SMTP credentials are valid"));
    }
    @PostMapping("/test-paypal")
    public ResponseEntity<ApiResponse<String>> testPaypal(@RequestBody java.util.Map<String, String> payload) {
        String clientId = payload.get("clientId");
        String secret = payload.get("secret");
        if (clientId == null || secret == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Client ID and Secret are required"));
        }
        service.testPaypalConnection(clientId, secret);
        return ResponseEntity.ok(ApiResponse.success("Connection successful", "PayPal credentials are valid"));
    }

    @PostMapping("/test-payos")
    public ResponseEntity<ApiResponse<String>> testPayos(@RequestBody java.util.Map<String, String> payload) {
        String clientId = payload.get("clientId");
        String apiKey = payload.get("apiKey");
        if (clientId == null || apiKey == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Client ID and API Key are required"));
        }
        service.testPayosConnection(clientId, apiKey);
        return ResponseEntity.ok(ApiResponse.success("Connection successful", "PayOS credentials are valid"));
    }

    @PostMapping("/test-gemini")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, String>>>> testGemini(@RequestBody java.util.Map<String, String> payload) {
        String apiKey = payload.get("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Gemini API Key is required"));
        }
        List<java.util.Map<String, String>> models = service.testGeminiConnectionAndGetModels(apiKey);
        return ResponseEntity.ok(ApiResponse.success(models, "Connection successful"));
    }

    @PostMapping("/test-gemini-model")
    public ResponseEntity<ApiResponse<String>> testGeminiModel(@RequestBody java.util.Map<String, String> payload) {
        String apiKey = payload.get("apiKey");
        String modelName = payload.get("model");
        if (apiKey == null || apiKey.trim().isEmpty() || modelName == null || modelName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "API Key and Model are required"));
        }
        String response = service.testSingleGeminiModel(apiKey, modelName);
        return ResponseEntity.ok(ApiResponse.success(response, "Connection successful"));
    }
}

