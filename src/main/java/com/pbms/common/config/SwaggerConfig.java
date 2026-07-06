package com.pbms.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Swagger/OpenAPI cho backend.
 * File này tạo tài liệu API cho hệ thống PBMS và cho phép test API bằng JWT token trên Swagger UI.
 * Các đường dẫn Swagger được mở public trong SecurityConfig.
 *
 * Pseudo code:
 * 1. Đánh dấu đây là file cấu hình của Spring.
 * 2. Tạo cấu hình OpenAPI cho project.
 * 3. Khai báo thông tin tài liệu API như tên, mô tả và phiên bản.
 * 4. Thêm cấu hình bảo mật bearerAuth để Swagger hỗ trợ nhập JWT token.
 * 5. Trả về OpenAPI để Swagger UI hiển thị tài liệu API.
 */
@Configuration
public class SwaggerConfig {

    /**
     * Tạo cấu hình OpenAPI dùng cho Swagger UI.
     * Method này giúp Swagger biết tên hệ thống, mô tả API, version và cách truyền JWT token.
     *
     * Pseudo code:
     * 1. Tạo một đối tượng OpenAPI mới.
     * 2. Gán thông tin API gồm title, description và version.
     * 3. Thêm security requirement tên bearerAuth.
     * 4. Tạo security scheme kiểu HTTP bearer.
     * 5. Khai báo token sử dụng định dạng JWT.
     * 6. Trả về cấu hình OpenAPI cho Spring quản lý.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("PBMS API")
                        .description("Parking Building Management System API Documentation")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(
                        new Components()
                                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                )
                );
    }
}