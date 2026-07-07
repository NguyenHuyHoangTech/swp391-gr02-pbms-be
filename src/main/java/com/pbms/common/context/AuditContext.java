package com.pbms.common.context;

import com.pbms.modules.identity.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chức năng của file:
 * AuditContext là class lưu thông tin tạm thời của một hành động cần ghi audit log.
 * File này được dùng trong lúc request đang chạy để truyền dữ liệu audit giữa AuditLogAspect,
 * HibernateAuditListener và AuditLogEventHandler.
 *
 * Luồng audit log liên quan:
 * - AuditLogAspect tạo AuditContext khi method có @LogAudit được gọi.
 * - AuditContextHolder giữ AuditContext theo từng thread/request.
 * - HibernateAuditListener đọc AuditContext khi entity bị insert, update hoặc delete.
 * - AuditLogEventHandler dùng dữ liệu từ AuditContext để tạo AuditLog và lưu vào database.
 *
 * Liên quan backend:
 * - User actor là người đang thực hiện hành động.
 * - action là tên hành động cần ghi log.
 * - resource là tài nguyên hoặc module bị tác động.
 * - oldValue và newValue lưu dữ liệu trước và sau khi thay đổi.
 * - dbModified cho biết HibernateAuditListener đã bắt được thay đổi database hay chưa.
 *
 * Pseudo code:
 * 1. Lưu action của hành động hiện tại.
 * 2. Lưu resource đang bị tác động.
 * 3. Lưu mô tả hành động.
 * 4. Lưu user đang thực hiện hành động.
 * 5. Lưu địa chỉ IP của request.
 * 6. Lưu dữ liệu cũ nếu có.
 * 7. Lưu dữ liệu mới nếu có.
 * 8. Đánh dấu request này có thay đổi database hay không.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditContext {
    private String action;
    private String resource;
    private String description;
    private User actor;
    private String ipAddress;
    private String oldValue;
    private String newValue;
    private boolean dbModified;
}