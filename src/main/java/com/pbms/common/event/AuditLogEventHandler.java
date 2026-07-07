package com.pbms.common.event;

import com.pbms.common.context.AuditContext;
import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chức năng của file:
 * AuditLogEventHandler là class xử lý AuditLogEvent sau khi event được publish trong hệ thống.
 * File này nhận dữ liệu audit từ AuditLogEvent, tạo entity AuditLog và lưu lịch sử thao tác vào database.
 *
 * Luồng audit log liên quan:
 * - AuditLogAspect tạo AuditContext khi method có @LogAudit được gọi.
 * - HibernateAuditListener bắt thay đổi entity và tạo AuditLogEvent.
 * - AuditLogEvent chứa thông tin context, entity bị tác động, id entity, oldValue và newValue.
 * - AuditLogEventHandler lắng nghe AuditLogEvent và lưu dữ liệu vào bảng audit_logs.
 *
 * Liên quan backend:
 * - AuditContext chứa thông tin người thao tác, hành động, tài nguyên, mô tả và địa chỉ IP.
 * - AuditLogEvent là event truyền dữ liệu audit trong nội bộ Spring.
 * - AuditLog là entity đại diện cho một dòng lịch sử thao tác.
 * - AuditLogRepository dùng để lưu AuditLog vào database.
 *
 * Pseudo code:
 * 1. Khai báo class này là Spring component.
 * 2. Inject AuditLogRepository để lưu audit log.
 * 3. Lắng nghe AuditLogEvent được publish trong hệ thống.
 * 4. Lấy AuditContext từ event.
 * 5. Ghép resource với tên entity và id entity để dễ truy vết.
 * 6. Tạo AuditLog từ dữ liệu trong context và event.
 * 7. Lưu AuditLog vào database bằng AuditLogRepository.
 * 8. Ghi log thông báo nếu lưu thành công.
 * 9. Ghi log lỗi nếu quá trình lưu thất bại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogEventHandler {

    private final AuditLogRepository auditLogRepository;

    /**
     * Xử lý AuditLogEvent và lưu lịch sử thao tác vào database.
     * Method này chạy trong transaction mới để việc lưu audit log độc lập hơn với transaction chính.
     *
     * Pseudo code:
     * 1. Nhận AuditLogEvent từ Spring event system.
     * 2. Lấy AuditContext bên trong event.
     * 3. Tạo detailedResource bằng resource, targetEntity và targetId.
     * 4. Tạo AuditLog bằng actor, action, resource, description, ipAddress, oldValue và newValue.
     * 5. Lưu AuditLog vào database.
     * 6. Ghi log thông báo lưu audit log thành công.
     * 7. Nếu có lỗi thì ghi log lỗi để hỗ trợ debug.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuditLogEvent(AuditLogEvent event) {
        try {
            AuditContext context = event.getAuditContext();

            String detailedResource = context.getResource() + " (" + event.getTargetEntity() + " #" + event.getTargetId() + ")";

            AuditLog auditLog = AuditLog.builder()
                    .actor(context.getActor())
                    .action(context.getAction())
                    .resource(detailedResource)
                    .description(context.getDescription())
                    .ipAddress(context.getIpAddress())
                    .oldValue(event.getOldValue())
                    .newValue(event.getNewValue())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Saved audit log for entity: {} (ID: {})", event.getTargetEntity(), event.getTargetId());
        } catch (Exception e) {
            log.error("Failed to save audit log from event: {}", e.getMessage(), e);
        }
    }
}