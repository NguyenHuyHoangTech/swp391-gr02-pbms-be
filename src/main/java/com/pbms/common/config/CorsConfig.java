package com.pbms.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;

/**
 * Cấu hình CORS cho hệ thống.
 * File này cho phép frontend được gọi API từ backend khi frontend và backend chạy khác domain hoặc khác port.
 * Ví dụ: frontend chạy ở localhost:5173, backend chạy ở localhost:8080.
 *
 * Pseudo code:
 * 1. Đọc danh sách origin được phép gọi API từ file cấu hình.
 * 2. Tạo cấu hình CORS cho backend.
 * 3. Cho phép các HTTP method cần dùng như GET, POST, PUT, DELETE.
 * 4. Cho phép frontend gửi header trong request.
 * 5. Cho phép gửi thông tin đăng nhập hoặc token nếu cần.
 * 6. Áp dụng cấu hình CORS cho toàn bộ API.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Tạo cấu hình CORS để Spring biết frontend nào được phép gọi API.
     *
     * Pseudo code:
     * 1. Tạo đối tượng CorsConfiguration.
     * 2. Gán danh sách frontend origin được phép truy cập.
     * 3. Cho phép các method GET, POST, PUT, DELETE, OPTIONS và PATCH.
     * 4. Cho phép tất cả request header.
     * 5. Cho phép gửi credentials như cookie hoặc authorization token.
     * 6. Đăng ký cấu hình này cho tất cả đường dẫn API.
     * 7. Trả về cấu hình để Spring Security hoặc Spring Web sử dụng.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}