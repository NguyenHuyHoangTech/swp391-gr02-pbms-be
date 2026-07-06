package com.pbms.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Cấu hình chạy các công việc theo lịch cho hệ thống.
 * File này cho phép project dùng @Scheduled để tự động chạy một hàm theo thời gian định sẵn.
 * Ví dụ: tự động kiểm tra vé hết hạn, tự động cập nhật trạng thái đặt chỗ, tự động dọn dữ liệu cũ.
 *
 * Pseudo code:
 * 1. Đánh dấu đây là file cấu hình của Spring.
 * 2. Bật chức năng chạy task theo lịch.
 * 3. Tạo scheduler để quản lý các task chạy định kỳ.
 * 4. Cho Spring sử dụng scheduler này cho các hàm có @Scheduled.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Tạo scheduler dùng để chạy các cron job hoặc task định kỳ.
     *
     * Pseudo code:
     * 1. Tạo ThreadPoolTaskScheduler.
     * 2. Cho phép scheduler dùng tối đa 5 thread.
     * 3. Đặt tên thread bắt đầu bằng "CronJob-" để dễ xem trong log.
     * 4. Khởi tạo scheduler.
     * 5. Trả về scheduler cho Spring quản lý.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("CronJob-");
        scheduler.initialize();
        return scheduler;
    }
}