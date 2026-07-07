package com.pbms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cấu hình để backend có thể trả về các file đã upload.
 * File này ánh xạ đường dẫn /uploads/** trên API tới thư mục uploads trong project.
 * FileStorageService có thể lưu file vào thư mục uploads, còn WebConfig giúp frontend truy cập lại các file đó.
 *
 * Pseudo code:
 * 1. Đánh dấu đây là file cấu hình của Spring.
 * 2. Implement WebMvcConfigurer để tùy chỉnh cách Spring MVC xử lý resource.
 * 3. Lấy đường dẫn thật của thư mục uploads.
 * 4. Đăng ký đường dẫn /uploads/** để trỏ tới thư mục uploads.
 * 5. Cho phép frontend mở file bằng URL dạng /uploads/ten-file.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Đăng ký resource handler cho các file trong thư mục uploads.
     * Method này giúp các file được lưu trên server có thể được truy cập qua URL.
     *
     * Pseudo code:
     * 1. Tạo đường dẫn tới thư mục uploads.
     * 2. Chuyển đường dẫn uploads thành absolute path.
     * 3. Đăng ký URL pattern /uploads/**.
     * 4. Trỏ URL pattern đó tới thư mục uploads trên máy chủ.
     * 5. Khi frontend gọi /uploads/file-name, Spring sẽ tìm file tương ứng trong thư mục uploads.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        
        registry.addResourceHandler("/uploads/**")
                 .addResourceLocations("file:/" + uploadPath + "/");
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}