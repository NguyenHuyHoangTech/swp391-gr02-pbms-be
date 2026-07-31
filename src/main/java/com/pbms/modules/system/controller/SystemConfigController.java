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

