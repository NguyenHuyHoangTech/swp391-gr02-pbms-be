package com.pbms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Cấu hình cơ chế retry cho hệ thống.
 * File này cho phép project dùng @Retryable để tự động chạy lại một method khi method đó bị lỗi tạm thời.
 * Ví dụ: khi có nhiều người cùng cập nhật một dữ liệu và xảy ra lỗi conflict/race condition.
 *
 * Pseudo code:
 * 1. Đánh dấu đây là một file cấu hình của Spring.
 * 2. Bật chức năng retry trong toàn bộ project.
 * 3. Cho phép các method có @Retryable được tự động chạy lại khi gặp lỗi phù hợp.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}