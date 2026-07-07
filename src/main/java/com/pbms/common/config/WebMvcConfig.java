package com.pbms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cấu hình Spring MVC cho các static resource của backend.
 * File này giúp frontend có thể truy cập các file đã được upload lên server.
 * FileStorageService có thể lưu file vào thư mục uploads, còn WebMvcConfig ánh xạ thư mục đó ra URL /uploads/**.
 *
 * Pseudo code:
 * 1. Đánh dấu đây là file cấu hình của Spring.
 * 2. Implement WebMvcConfigurer để tùy chỉnh cấu hình Spring MVC.
 * 3. Tìm đường dẫn thật của thư mục uploads trong project.
 * 4. Đăng ký URL /uploads/** để trỏ tới thư mục uploads.
 * 5. Khi frontend gọi /uploads/tên-file, backend sẽ trả về file tương ứng.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Đăng ký đường dẫn truy cập cho các file upload.
     * Method này cho phép file trong thư mục uploads được mở thông qua URL.
     *
     * Pseudo code:
     * 1. Lấy thư mục uploads.
     * 2. Chuyển thư mục uploads thành đường dẫn tuyệt đối.
     * 3. Đăng ký URL pattern /uploads/**.
     * 4. Gắn URL pattern đó với thư mục uploads trên máy chủ.
     * 5. Cho phép frontend truy cập file bằng đường dẫn /uploads/file-name.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}