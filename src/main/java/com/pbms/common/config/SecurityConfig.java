package com.pbms.common.config;

import com.pbms.common.security.IoTAuthenticationFilter;
import com.pbms.common.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cấu hình bảo mật chính cho backend.
 * File này quản lý việc xác thực, phân quyền và filter bảo mật cho toàn bộ API.
 * SecurityConfig kết hợp với JwtAuthFilter, IoTAuthenticationFilter và CorsConfig
 * để kiểm soát request từ frontend, thiết bị IoT và người dùng đã đăng nhập.
 *
 * Pseudo code:
 * 1. Bật Spring Security cho project.
 * 2. Bật kiểm tra quyền bằng annotation ở cấp method.
 * 3. Cấu hình backend chạy theo kiểu stateless vì hệ thống dùng JWT.
 * 4. Khai báo nhóm API public không cần đăng nhập.
 * 5. Khai báo nhóm API cần role ADMIN, MANAGER, STAFF hoặc CUSTOMER.
 * 6. Gắn IoTAuthenticationFilter để xử lý request từ thiết bị IoT.
 * 7. Gắn JwtAuthFilter để xử lý request có JWT token.
 * 8. Tạo PasswordEncoder để mã hóa mật khẩu người dùng.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final IoTAuthenticationFilter ioTAuthenticationFilter;

    /**
     * Nhận các filter bảo mật từ package common.security.
     * JwtAuthFilter dùng để xác thực người dùng bằng JWT token.
     * IoTAuthenticationFilter dùng để xác thực request từ thiết bị IoT.
     *
     * Pseudo code:
     * 1. Nhận JwtAuthFilter từ Spring container.
     * 2. Nhận IoTAuthenticationFilter từ Spring container.
     * 3. Lưu hai filter này vào biến của class để gắn vào SecurityFilterChain.
     */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter, IoTAuthenticationFilter ioTAuthenticationFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.ioTAuthenticationFilter = ioTAuthenticationFilter;
    }

    /**
     * Cấu hình rule bảo mật cho toàn bộ HTTP request.
     * Method này quyết định API nào được truy cập tự do và API nào cần đúng role.
     * CORS được lấy theo CorsConfig, còn JWT và IoT authentication được xử lý bằng filter riêng.
     *
     * Pseudo code:
     * 1. Tắt CSRF vì backend dùng JWT và không dùng session login truyền thống.
     * 2. Bật CORS để frontend có thể gọi API backend.
     * 3. Đặt session là STATELESS để server không lưu phiên đăng nhập.
     * 4. Cho phép các API public, auth, webhook, IoT, Swagger, WebSocket và upload truy cập tự do.
     * 5. Chỉ cho ADMIN truy cập nhóm API admin.
     * 6. Cho MANAGER và ADMIN truy cập nhóm API manager và report.
     * 7. Cho STAFF, MANAGER và ADMIN truy cập nhóm API vận hành nội bộ.
     * 8. Cho CUSTOMER truy cập các API dành cho khách hàng.
     * 9. Bắt buộc các request còn lại phải đăng nhập.
     * 10. Tắt frame options để H2 console có thể hiển thị nếu project dùng H2.
     * 11. Gắn IoTAuthenticationFilter trước UsernamePasswordAuthenticationFilter.
     * 12. Gắn JwtAuthFilter trước UsernamePasswordAuthenticationFilter.
     * 13. Build và trả về SecurityFilterChain cho Spring Security sử dụng.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/public/**",
                                "/api/v1/identity/auth/**",
                                "/api/v1/webhooks/**",
                                "/api/v1/iot/**",
                                "/api/v1/operation/iot/**"
                        ).permitAll()
                        .requestMatchers(
                                "/h2-console/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(
                                "/ws/**",
                                "/ws-pbms/**",
                                "/uploads/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                "/api/v1/manager/**",
                                "/api/v1/reports/**"
                        ).hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/dashboard/**")
                        .hasAnyRole("STAFF", "MANAGER", "ADMIN")
                        .requestMatchers(
                                "/api/v1/gates/**",
                                "/api/v1/work-sessions/**"
                        ).hasAnyRole("STAFF", "MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/payments/**")
                        .hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                        .requestMatchers(
                                "/api/v1/operation/monthly-tickets",
                                "/api/v1/operation/monthly-tickets/**"
                        ).hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                        .requestMatchers("/api/v1/operation/**")
                        .hasAnyRole("STAFF", "MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/incidents/**")
                        .hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                        .requestMatchers("/api/v1/parking-sessions/**")
                        .hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                        .requestMatchers("/api/v1/infrastructure/**")
                        .hasAnyRole("STAFF", "MANAGER", "ADMIN", "CUSTOMER")
                        .requestMatchers("/api/v1/customer/reservations/*/resolve-conflict")
                        .hasAnyRole("STAFF", "CUSTOMER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/customer/**")
                        .hasAnyRole("CUSTOMER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/user/**")
                        .hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        http.addFilterBefore(ioTAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Tạo PasswordEncoder dùng để mã hóa và kiểm tra mật khẩu.
     * BCryptPasswordEncoder giúp lưu password dưới dạng hash thay vì lưu password gốc.
     *
     * Pseudo code:
     * 1. Tạo BCryptPasswordEncoder.
     * 2. Trả về PasswordEncoder cho Spring sử dụng trong quá trình đăng ký và đăng nhập.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}