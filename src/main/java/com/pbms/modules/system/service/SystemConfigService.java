// TO BE IMPLEMENTED BY MEMBER 1 (CORE ARCHITECT & CLOUD DEVOPS)
package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Service: Đánh dấu class này là một Service Component trong Spring Boot.
 * Nó chứa các logic nghiệp vụ (business logic) liên quan đến việc quản lý cấu hình hệ thống (System Configuration).
 */
@Service
public class SystemConfigService {

    // Khai báo repository để tương tác với cơ sở dữ liệu của bảng SystemConfig.
    // Dùng từ khóa 'final' để đảm bảo tính bất biến (immutable) sau khi khởi tạo.
    private final SystemConfigRepository repository;

    /**
     * Constructor Injection: Tiêm (inject) SystemConfigRepository vào service.
     * Đây là cách Best Practice được khuyến nghị bởi Spring thay vì dùng @Autowired.
     */
    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Lấy danh sách toàn bộ các cấu hình hệ thống hiện có trong database.
     * @return Danh sách các SystemConfig.
     */
    public List<SystemConfig> getAllConfigs() {
        return repository.findAll();
    }

    /**
     * Tìm kiếm một cấu hình cụ thể dựa trên khóa (key) của nó.
     * @param key Khóa cấu hình (Ví dụ: "PAYPAL_CLIENT_ID")
     * @return Đối tượng SystemConfig nếu tìm thấy.
     * @throws IllegalArgumentException Nếu không tìm thấy key trong database.
     */
    public SystemConfig getConfigByKey(String key) {
        return repository.findByConfigKey(key)
                // orElseThrow giúp ném ra lỗi ngay lập tức nếu dữ liệu rỗng (Optional is empty)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with key: " + key));
    }

    /**
     * Lưu mới hoặc cập nhật một cấu hình đã tồn tại.
     * @Transactional: Đảm bảo tính toàn vẹn dữ liệu. Nếu có lỗi xảy ra trong hàm này, mọi thay đổi DB sẽ bị rollback (hủy bỏ).
     * * @param key   Khóa cấu hình cần lưu/cập nhật.
     * @param value Giá trị mới của cấu hình.
     * @return Đối tượng SystemConfig vừa được lưu.
     */
    @Transactional
    public SystemConfig saveOrUpdateConfigValue(String key, String value) {
        // Tìm cấu hình theo key, nếu không có thì trả về null
        SystemConfig config = repository.findByConfigKey(key).orElse(null);

        if (config == null) {
            // Nếu chưa tồn tại -> Tạo mới bằng Builder pattern
            config = SystemConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description("Default system configuration")
                    .build();
            return repository.save(config);
        } else {
            // Nếu đã tồn tại -> Chỉ cập nhật giá trị (value) mới
            config.setConfigValue(value);
            return repository.save(config);
        }
    }

    /**
     * Tạo mới một cấu hình (Yêu cầu config key không được trùng lặp).
     */
    @Transactional
    public SystemConfig createConfig(SystemConfig config) {
        // Kiểm tra xem key này đã bị ai đó dùng trong DB chưa
        if (repository.findByConfigKey(config.getConfigKey()).isPresent()) {
            throw new IllegalArgumentException("Config key already exists: " + config.getConfigKey());
        }
        return repository.save(config);
    }

    /**
     * Cập nhật thông tin của một cấu hình dựa trên ID (Khóa chính).
     */
    @Transactional
    public SystemConfig updateConfig(Long id, SystemConfig configDetails) {
        // Tìm cấu hình theo ID trước, nếu không có thì báo lỗi
        SystemConfig config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with id: " + id));

        // Cập nhật giá trị và mô tả mới
        config.setConfigValue(configDetails.getConfigValue());
        config.setDescription(configDetails.getDescription());

        return repository.save(config); // Lưu lại thay đổi xuống DB
    }

    /**
     * Xóa một cấu hình hệ thống khỏi database dựa trên ID.
     */
    @Transactional
    public void deleteConfig(Long id) {
        // Kiểm tra xem cấu hình có tồn tại không trước khi xóa
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Config not found with id: " + id);
        }
        repository.deleteById(id);
    }

    /**
     * TEST CONNECTION: Kiểm tra kết nối đến máy chủ Gửi Email (SMTP của Gmail).
     * Hàm này rất hữu ích để validate cấu hình email do người dùng nhập vào trên giao diện trước khi lưu.
     */
    public void testSmtpConnection(String email, String appPassword) {
        // Khởi tạo đối tượng gửi mail của Spring
        org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com"); // Máy chủ SMTP của Google
        mailSender.setPort(587);              // Cổng chuẩn hỗ trợ TLS
        mailSender.setUsername(email);        // Email người gửi
        mailSender.setPassword(appPassword);  // Mật khẩu ứng dụng (App Password)

        // Cấu hình các thuộc tính bảo mật cần thiết cho Gmail
        java.util.Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        // Thiết lập thời gian chờ (timeout) là 5 giây (5000ms) để tránh bị treo hệ thống nếu mạng lag
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        try {
            // Thử kết nối đến máy chủ SMTP bằng thông tin đã nhập
            mailSender.testConnection();
        } catch (jakarta.mail.MessagingException e) {
            // Nếu kết nối thất bại (sai pass, sai email, mạng lỗi) -> ném ra lỗi để báo cho người dùng
            throw new IllegalArgumentException("SMTP Connection failed: " + e.getMessage());
        }
    }

    /**
     * TEST CONNECTION: Kiểm tra kết nối đến cổng thanh toán PayPal (Môi trường Sandbox).
     */
    public void testPaypalConnection(String clientId, String secret) {
        // Sử dụng RestTemplate để gọi API HTTP bên ngoài
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

        // Cấu hình xác thực Basic Auth (Mã hóa Client ID và Secret)
        headers.setBasicAuth(clientId, secret);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        // Body của request yêu cầu cấp token (OAuth2)
        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>("grant_type=client_credentials", headers);

        try {
            // Gửi request POST đến API tạo token của PayPal Sandbox
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api-m.sandbox.paypal.com/v1/oauth2/token", request, String.class);

            // Nếu HTTP status không phải dạng 2xx (Thành công) -> Thông tin sai
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("PayPal Connection failed: Invalid credentials");
            }
        } catch (Exception e) {
            // Bắt các lỗi mạng hoặc HTTP lỗi (401 Unauthorized) do sai Client ID/Secret
            throw new IllegalArgumentException("PayPal Connection failed: " + e.getMessage());
        }
    }

    /**
     * TEST CONNECTION: Kiểm tra kết nối đến cổng thanh toán PayOS.
     */
    public void testPayosConnection(String clientId, String apiKey) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

        // PayOS yêu cầu truyền Client ID và API Key qua header tùy chỉnh
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(headers);

        try {
            // Gửi một request GET giả lập (dummy) đến 1 mã đơn hàng ngẫu nhiên (123456)
            restTemplate.exchange(
                    "https://api-merchant.payos.vn/v2/payment-requests/123456",
                    org.springframework.http.HttpMethod.GET,
                    request, String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Nếu API trả về lỗi 401 UNAUTHORIZED -> Chắc chắn Client ID hoặc API Key bị sai
            if (e.getStatusCode() == org.springframework.http.HttpStatus.UNAUTHORIZED) {
                throw new IllegalArgumentException("PayOS Connection failed: Invalid credentials");
            }
            // Nếu trả về lỗi khác (như 404 Not Found do đơn hàng 123456 không tồn tại)
            // thì chứng tỏ thông tin xác thực đã đúng, kết nối qua PayOS thành công, nên bỏ qua không ném lỗi.
        } catch (Exception e) {
            // Các lỗi khác như đứt cáp, sập server PayOS...
            throw new IllegalArgumentException("PayOS Connection failed: " + e.getMessage());
        }
    }
}
