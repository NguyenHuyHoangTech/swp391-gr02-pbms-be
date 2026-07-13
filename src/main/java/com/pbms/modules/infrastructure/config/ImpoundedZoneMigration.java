/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-13
 * @Description: On application startup, updates any zone whose function_type is still 'IMPOUNDED'
 *               to 'WALK_IN'. The IMPOUNDED type is no longer handled by the current business logic,
 *               so this keeps existing zone data consistent with that logic.
 * @Dependencies:
 * - JdbcTemplate (org.springframework.jdbc.core.JdbcTemplate)
 * - CommandLineRunner (org.springframework.boot.CommandLineRunner)
 */
package com.pbms.modules.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ImpoundedZoneMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImpoundedZoneMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public ImpoundedZoneMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @Function: run
     * @Description: Chạy 1 lần mỗi khi ứng dụng khởi động, chuyển hết những zone IMPOUNDED còn sót lại
     *               (từ dữ liệu cũ trước khi bỏ tính năng) về WALK_IN. Dùng JdbcTemplate cập nhật thẳng
     *               SQL thay vì qua JPA vì đây là thao tác dọn dữ liệu 1 lần, không cần load entity qua Hibernate.
     * @Logic_Steps:
     * 1. Cập nhật mọi zone có function_type = 'IMPOUNDED' thành 'WALK_IN' bằng 1 câu UPDATE.
     * 2. Nếu có dòng bị đổi, ghi log số lượng zone đã migrate; ngược lại ghi log là không cần migrate.
     * 3. Bọc trong try/catch để lỗi migrate không làm chặn quá trình khởi động ứng dụng.
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("Checking for any remaining IMPOUNDED zones to migrate...");
        try {
            int updatedRows = jdbcTemplate.update(
                "UPDATE zones SET function_type = 'WALK_IN' WHERE function_type = 'IMPOUNDED'"
            );
            if (updatedRows > 0) {
                log.info("Migrated {} IMPOUNDED zones to WALK_IN zones successfully.", updatedRows);
            } else {
                log.info("No IMPOUNDED zones found. Migration not needed.");
            }
        } catch (Exception e) {
            log.error("Failed to execute IMPOUNDED zone migration.", e);
        }
    }
}
