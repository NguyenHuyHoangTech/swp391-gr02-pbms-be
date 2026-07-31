/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ REQUEST CỦA HỆ THỐNG (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO BỘ ĐIỀU PHỐI VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Khai báo @RestController báo cho Spring Boot biết class này là một Web API.
 * - Minh chứng 2: Khai báo @RequiredArgsConstructor (hoặc Constructor) để Spring Boot 
 *   tự động tiêm các Service vào biến cục bộ (Constructor Injection).
 * 
 * BƯỚC 2: TIẾP NHẬN REQUEST TỪ CLIENT (DISPATCHER SERVLET & HANDLER MAPPING)
 * - Minh chứng 1: Khai báo @RequestMapping ở đầu class quy định Gốc của URL.
 * - Minh chứng 2: Các hàm được đánh dấu @PostMapping, @GetMapping, @PutMapping 
 *   là bằng chứng cho việc HandlerMapping sẽ định tuyến chính xác mọi Request vào đúng hàm.
 * 
 * BƯỚC 3: RÀNG BUỘC VÀ CHUYỂN ĐỔI DỮ LIỆU (DESERIALIZATION)
 * - Minh chứng: Các tham số @RequestBody, @PathVariable, @RequestParam kích hoạt 
 *   thư viện Jackson đọc chuỗi văn bản JSON thành dạng Object Java.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.pbms.common.annotation.LogAudit;

@RestController
@RequestMapping("/api/v1/system/configs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'STAFF')")
public class SystemConfigController {

    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    /**
     * =========================================================================
     * NGHIỆP VỤ: GETALLCONFIGS
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho getAllConfigs.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<List<SystemConfig>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(service.getAllConfigs(), "Configs fetched successfully"));
    }

    @GetMapping("/{key}")
    /**
     * =========================================================================
     * NGHIỆP VỤ: GETCONFIGBYKEY
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho getConfigByKey.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<SystemConfig>> getConfigByKey(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(service.getConfigByKey(key), "Config fetched successfully"));
    }

    @PostMapping
    @LogAudit(action = "CREATE", resource = "System/PenaltyConfig", description = "Create new system or penalty configuration")
    /**
     * =========================================================================
     * NGHIỆP VỤ: CREATECONFIG
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho createConfig.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<SystemConfig>> createConfig(@RequestBody SystemConfig config) {
        return ResponseEntity.ok(ApiResponse.success(service.createConfig(config), "Config created successfully"));
    }

    @PutMapping("/{id}")
    @LogAudit(action = "UPDATE", resource = "System/PenaltyConfig", description = "Update system or penalty configuration")
    /**
     * =========================================================================
     * NGHIỆP VỤ: UPDATECONFIG
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho updateConfig.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<SystemConfig>> updateConfig(@PathVariable Long id, @RequestBody SystemConfig configDetails) {
        return ResponseEntity.ok(ApiResponse.success(service.updateConfig(id, configDetails), "Config updated successfully"));
    }

    @DeleteMapping("/{id}")
    @LogAudit(action = "DELETE", resource = "System/PenaltyConfig", description = "Delete system or penalty configuration")
    /**
     * =========================================================================
     * NGHIỆP VỤ: DELETECONFIG
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho deleteConfig.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable Long id) {
        service.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Config deleted successfully"));
    }

    @PostMapping("/test-email")
    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTEMAIL
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testEmail.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
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
    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTPAYPAL
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testPaypal.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
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
    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTPAYOS
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testPayos.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
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
    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTGEMINIMODEL
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testGeminiModel.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
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

