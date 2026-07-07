package com.pbms.common.event;

import com.pbms.common.context.AuditContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Chức năng của file:
 * AuditLogEvent là event dùng để truyền dữ liệu audit log trong hệ thống PBMS.
 * File này đóng vai trò là object trung gian chứa thông tin thay đổi entity,
 * sau đó gửi sang event listener hoặc event handler để lưu audit log.
 *
 * Luồng audit log liên quan:
 * - AuditLogAspect tạo AuditContext cho method có @LogAudit.
 * - HibernateAuditListener bắt sự kiện insert, update hoặc delete entity.
 * - HibernateAuditListener tạo AuditLogEvent chứa thông tin entity bị thay đổi.
 * - Event handler nhận AuditLogEvent và lưu dữ liệu vào bảng audit_logs.
 *
 * Liên quan backend:
 * - AuditContext chứa actor, action, resource, description và ipAddress.
 * - ApplicationEvent cho phép Spring publish và lắng nghe event nội bộ.
 * - HibernateAuditListener là nơi tạo và publish AuditLogEvent.
 * - AuditLogRepository thường được dùng ở event handler để lưu log vào database.
 *
 * Pseudo code:
 * 1. Nhận AuditContext từ luồng audit hiện tại.
 * 2. Nhận tên entity bị tác động.
 * 3. Nhận id của entity bị tác động.
 * 4. Nhận dữ liệu cũ của entity.
 * 5. Nhận dữ liệu mới của entity.
 * 6. Đóng gói toàn bộ thông tin thành một event.
 * 7. Cho Spring publish event để phần xử lý khác lưu audit log.
 */
@Getter
public class AuditLogEvent extends ApplicationEvent {

    private final AuditContext auditContext;
    private final String oldValue;
    private final String newValue;
    private final String targetEntity;
    private final Long targetId;

    /**
     * Tạo AuditLogEvent chứa thông tin thay đổi entity để gửi sang event handler.
     *
     * Pseudo code:
     * 1. Gọi constructor của ApplicationEvent bằng source.
     * 2. Gán AuditContext của hành động hiện tại.
     * 3. Gán tên entity bị tác động.
     * 4. Gán id của entity bị tác động.
     * 5. Gán dữ liệu cũ trước khi thay đổi.
     * 6. Gán dữ liệu mới sau khi thay đổi.
     */
    public AuditLogEvent(Object source, AuditContext auditContext, String targetEntity, Long targetId, String oldValue, String newValue) {
        super(source);
        this.auditContext = auditContext;
        this.targetEntity = targetEntity;
        this.targetId = targetId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}