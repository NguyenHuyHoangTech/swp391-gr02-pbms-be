package com.pbms.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình để hệ thống có thể chạy một số công việc ở background.
 * Ví dụ: gửi email, ghi log, gửi thông báo hoặc xử lý file mà không bắt người dùng phải chờ.
 *
 * Pseudo code:
 * 1. Bật chức năng chạy task bất đồng bộ.
 * 2. Tạo một nhóm thread riêng để xử lý các task background.
 * 3. Cho Spring quản lý nhóm thread này để các hàm có @Async sử dụng.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Tạo thread pool dùng cho các task chạy background.
     *
     * Pseudo code:
     * 1. Tạo executor.
     * 2. Cho phép chạy sẵn 5 thread.
     * 3. Khi nhiều việc hơn, có thể tăng tối đa lên 10 thread.
     * 4. Nếu task quá nhiều, cho tối đa 500 task chờ trong hàng đợi.
     * 5. Đặt tên thread bắt đầu bằng "PbmsAsync-" để dễ xem trong log.
     * 6. Khởi tạo executor và trả về cho Spring sử dụng.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("PbmsAsync-");
        executor.initialize();
        return executor;
    }
}