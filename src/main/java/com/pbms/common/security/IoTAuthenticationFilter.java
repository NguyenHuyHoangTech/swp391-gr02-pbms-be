package com.pbms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter dùng để xác thực request từ thiết bị IoT hoặc phần cứng.
 * File này kiểm tra API key trong header X-API-KEY trước khi cho phép request đi tiếp.
 * IoTAuthenticationFilter được gắn vào SecurityFilterChain trong SecurityConfig.
 *
 * Pseudo code:
 * 1. Mỗi request đi vào backend sẽ đi qua filter này một lần.
 * 2. Kiểm tra request có phải API dành cho IoT hay không.
 * 3. Nếu là API IoT thì đọc header X-API-KEY.
 * 4. So sánh API key nhận được với API key được cấu hình trong application.properties.
 * 5. Nếu API key sai hoặc thiếu thì trả về lỗi 401 Unauthorized.
 * 6. Nếu hợp lệ thì cho request đi tiếp đến filter/controller tiếp theo.
 */
@Component
public class IoTAuthenticationFilter extends OncePerRequestFilter {

    @Value("${iot.api.key:PBMS-HARDWARE-SECURE-KEY-2024}")
    private String expectedApiKey;

    /**
     * Xử lý xác thực API key cho các request IoT.
     * Method này chỉ kiểm tra các URL bắt đầu bằng /api/v1/operation/iot/ hoặc /api/v1/iot/.
     *
     * Pseudo code:
     * 1. Lấy đường dẫn hiện tại của request.
     * 2. Kiểm tra đường dẫn có thuộc nhóm API IoT hay không.
     * 3. Nếu không phải API IoT thì bỏ qua kiểm tra API key.
     * 4. Nếu là API IoT thì lấy header X-API-KEY từ request.
     * 5. Nếu header không tồn tại hoặc API key không đúng thì trả về lỗi 401.
     * 6. Nếu API key hợp lệ thì cho request tiếp tục đi qua filter chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getServletPath();

        if (path.startsWith("/api/v1/operation/iot/") || path.startsWith("/api/v1/iot/")) {
            String apiKey = request.getHeader("X-API-KEY");

            if (apiKey == null || !apiKey.equals(expectedApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\": 401, \"message\": \"Unauthorized: Invalid or missing API Key\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}