/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ NGHIỆP VỤ (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO DỊCH VỤ VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Ký hiệu @Service báo cho Spring Boot biết class này chứa Logic lõi. 
 *   Spring sẽ khởi tạo nó thành Singleton Bean.
 * - Minh chứng 2: Dùng @RequiredArgsConstructor để tự động tiêm các Repository vào Service.
 * 
 * BƯỚC 2: BẢO ĐẢM TOÀN VẸN GIAO DỊCH (TRANSACTION MANAGEMENT)
 * - Minh chứng: Các hàm thay đổi dữ liệu được gắn @Transactional. Điều này đảm bảo 
 *   khi có lỗi xảy ra, toàn bộ thao tác DB sẽ được Rollback, không gây rác dữ liệu.
 * 
 * BƯỚC 3: THỰC THI LOGIC NGHIỆP VỤ
 * - Minh chứng: Gọi các hàm từ Repository (như indById, save) để tương tác 
 *   trực tiếp với CSDL, xử lý các ngoại lệ (Exception) và trả về DTO cho Controller.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

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
    public List<SystemConfig> getAllConfigs() {
        return repository.findAll();
    }

    @org.springframework.cache.annotation.Cacheable(value = "configs", key = "#key")
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
    public SystemConfig getConfigByKey(String key) {
        return repository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with key: " + key));
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "configs", key = "#key")
    /**
     * =========================================================================
     * NGHIỆP VỤ: SAVEORUPDATECONFIGVALUE
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho saveOrUpdateConfigValue.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public SystemConfig saveOrUpdateConfigValue(String key, String value) {
        SystemConfig config = repository.findByConfigKey(key).orElse(null);
        if (config == null) {
            config = SystemConfig.builder()
                .configKey(key)
                .configValue(value)
                .description("Default system configuration")
                .build();
            return repository.save(config);
        } else {
            config.setConfigValue(value);
            return repository.save(config);
        }
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "configs", key = "#config.configKey")
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
    public SystemConfig createConfig(SystemConfig config) {
        if (repository.findByConfigKey(config.getConfigKey()).isPresent()) {
            throw new IllegalArgumentException("Config key already exists: " + config.getConfigKey());
        }
        return repository.save(config);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "configs", key = "#configDetails.configKey", allEntries = true)
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
    public SystemConfig updateConfig(Long id, SystemConfig configDetails) {
        SystemConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with id: " + id));

        config.setConfigValue(configDetails.getConfigValue());
        config.setDescription(configDetails.getDescription());
        
        return repository.save(config);
    }

    @Transactional
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
    public void deleteConfig(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Config not found with id: " + id);
        }
        repository.deleteById(id);
    }

    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTSMTPCONNECTION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testSmtpConnection.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public void testSmtpConnection(String email, String appPassword) {
        org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(email);
        mailSender.setPassword(appPassword);

        java.util.Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        // Enforce 5000ms timeout
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        try {
            mailSender.testConnection();
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalArgumentException("SMTP Connection failed: " + e.getMessage());
        }
    }
    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTPAYPALCONNECTION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testPaypalConnection.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public void testPaypalConnection(String clientId, String secret) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>("grant_type=client_credentials", headers);

        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api-m.sandbox.paypal.com/v1/oauth2/token", request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("PayPal Connection failed: Invalid credentials");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("PayPal Connection failed: " + e.getMessage());
        }
    }
    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTPAYOSCONNECTION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testPayosConnection.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public void testPayosConnection(String clientId, String apiKey) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(headers);

        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    "https://api-merchant.payos.vn/v2/payment-requests/123456", 
                    org.springframework.http.HttpMethod.GET, 
                    request, String.class);
                    
            String body = response.getBody();
            if (body != null) {
                if (body.contains("\"code\":\"214\"")) {
                    throw new IllegalArgumentException("PayOS Connection failed: Invalid Client ID (Cổng thanh toán không tồn tại)");
                }
                if (body.contains("\"code\":\"401\"")) {
                    throw new IllegalArgumentException("PayOS Connection failed: Invalid API Key or Unauthorized");
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == org.springframework.http.HttpStatus.UNAUTHORIZED) {
                throw new IllegalArgumentException("PayOS Connection failed: Invalid credentials");
            }
            // Other errors like 404 mean credentials are fine but record not found
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("PayOS Connection failed: " + e.getMessage());
        }
    }

    public List<java.util.Map<String, String>> testGeminiConnectionAndGetModels(String apiKey) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.getForEntity(
                    "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String body = response.getBody();
                List<java.util.Map<String, String>> testedModels = new java.util.ArrayList<>();
                
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
                    com.fasterxml.jackson.databind.JsonNode modelsNode = root.get("models");
                    if (modelsNode != null && modelsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode node : modelsNode) {
                            String name = node.get("name") != null ? node.get("name").asText() : "";
                            
                            // Check if it's a Gemini model and supports generateContent
                            boolean isGemini = name.contains("gemini");
                            boolean supportsGenerate = false;
                            com.fasterxml.jackson.databind.JsonNode methods = node.get("supportedGenerationMethods");
                            if (methods != null && methods.isArray()) {
                                for (com.fasterxml.jackson.databind.JsonNode method : methods) {
                                    if ("generateContent".equals(method.asText())) {
                                        supportsGenerate = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (isGemini && supportsGenerate) {
                                // TEST THE MODEL
                                String url = "https://generativelanguage.googleapis.com/v1beta/" + name + ":generateContent?key=" + apiKey;
                                
                                com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ArrayNode contents = payload.putArray("contents");
                                com.fasterxml.jackson.databind.node.ObjectNode content = contents.addObject();
                                content.put("role", "user");
                                com.fasterxml.jackson.databind.node.ArrayNode parts = content.putArray("parts");
                                com.fasterxml.jackson.databind.node.ObjectNode part = parts.addObject();
                                part.put("text", "Hi");
                                
                                com.fasterxml.jackson.databind.node.ObjectNode generationConfig = payload.putObject("generationConfig");
                                generationConfig.put("maxOutputTokens", 5);
                                
                                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(mapper.writeValueAsString(payload), headers);
                                
                                // Sleep 500ms to avoid burst rate-limits on Free Tier
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                               
                                
                                try {
                                    org.springframework.http.ResponseEntity<String> genResponse = restTemplate.postForEntity(url, entity, String.class);
                                    if (genResponse.getStatusCode().is2xxSuccessful()) {
                                        com.fasterxml.jackson.databind.JsonNode genRoot = mapper.readTree(genResponse.getBody());
                                        String replyText = genRoot.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
                                        
                                        java.util.Map<String, String> resMap = new java.util.HashMap<>();
                                        resMap.put("model", name);
                                        resMap.put("response", replyText.trim());
                                        testedModels.add(resMap);
                                    }
                                } catch (Exception ex) {
                                    // Skip models that fail to generate
                                    java.util.Map<String, String> resMap = new java.util.HashMap<>();
                                    resMap.put("model", name);
                                    resMap.put("response", "Error: " + ex.getMessage());
                                    testedModels.add(resMap);
                                }
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    throw new IllegalArgumentException("Failed to parse Gemini API response");
                }
                
                if (testedModels.isEmpty()) {
                    throw new IllegalArgumentException("API Key is valid but no compatible Gemini models found.");
                }
                return testedModels;
            } else {
                throw new IllegalArgumentException("Gemini Connection failed: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new IllegalArgumentException("Gemini Connection failed: Invalid API Key");
        } catch (Exception e) {
            throw new IllegalArgumentException("Gemini Connection failed: " + e.getMessage());
        }
    }

    /**
     * =========================================================================
     * NGHIỆP VỤ: TESTSINGLEGEMINIMODEL
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho testSingleGeminiModel.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public String testSingleGeminiModel(String apiKey, String modelName) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String url = "https://generativelanguage.googleapis.com/v1beta/" + modelName + ":generateContent?key=" + apiKey;
            
            com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode contents = payload.putArray("contents");
            com.fasterxml.jackson.databind.node.ObjectNode content = contents.addObject();
            content.put("role", "user");
            com.fasterxml.jackson.databind.node.ArrayNode parts = content.putArray("parts");
            com.fasterxml.jackson.databind.node.ObjectNode part = parts.addObject();
            part.put("text", "Hi");
            
            com.fasterxml.jackson.databind.node.ObjectNode generationConfig = payload.putObject("generationConfig");
            generationConfig.put("maxOutputTokens", 5);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(mapper.writeValueAsString(payload), headers);
            
            org.springframework.http.ResponseEntity<String> genResponse = restTemplate.postForEntity(url, entity, String.class);
            if (genResponse.getStatusCode().is2xxSuccessful()) {
                com.fasterxml.jackson.databind.JsonNode genRoot = mapper.readTree(genResponse.getBody());
                return genRoot.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
            } else {
                throw new IllegalArgumentException("Model test failed with status " + genResponse.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
             throw new IllegalArgumentException("Gemini Connection failed: " + e.getMessage());
        } catch (Exception e) {
             throw new IllegalArgumentException("Gemini Connection failed: " + e.getMessage());
        }
    }
}

