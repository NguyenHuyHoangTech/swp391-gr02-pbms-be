package com.pbms.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbms.common.context.AuditContext;
import com.pbms.common.context.AuditContextHolder;
import com.pbms.common.event.AuditLogEvent;
import com.pbms.modules.system.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Chức năng của file:
 * HibernateAuditListener là listener dùng để bắt sự kiện thay đổi entity trong Hibernate.
 * File này theo dõi các hành động insert, update và delete của entity để tạo dữ liệu audit log.
 *
 * Luồng audit log trong package common.aspect:
 * - AuditLogAspect tạo AuditContext khi method có @LogAudit được gọi.
 * - AuditContextHolder giữ AuditContext trong request hiện tại.
 * - HibernateAuditListener đọc AuditContext khi Hibernate phát hiện entity bị thay đổi.
 * - HibernateAuditListener chuyển oldState và newState thành JSON.
 * - HibernateAuditListener phát AuditLogEvent để phần xử lý event lưu audit log.
 * - HibernateListenerConfig đăng ký listener này vào Hibernate lifecycle.
 *
 * Liên quan backend:
 * - AuditContext chứa actor, action, resource, description và ipAddress của hành động hiện tại.
 * - AuditContextHolder cho phép listener lấy đúng context của request hiện tại.
 * - AuditLogEvent dùng để gửi dữ liệu audit sang event handler.
 * - AuditLog được bỏ qua để tránh việc ghi audit log cho chính bảng audit_logs.
 *
 * Pseudo code:
 * 1. Lắng nghe sự kiện entity được tạo mới, cập nhật hoặc xóa.
 * 2. Bỏ qua nếu entity hiện tại là AuditLog.
 * 3. Lấy AuditContext hiện tại từ AuditContextHolder.
 * 4. Bỏ qua một số entity không cần ghi log tự động hoặc đã được xử lý dạng batch.
 * 5. Nếu không có AuditContext thì không ghi log.
 * 6. Đánh dấu context là đã có thay đổi database.
 * 7. Chuyển trạng thái cũ và trạng thái mới của entity thành JSON.
 * 8. Lấy id của entity bằng reflection.
 * 9. Tạo AuditLogEvent chứa context, tên entity, id entity và newValue.
 * 10. Publish event để phần xử lý khác lưu audit log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HibernateAuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /**
     * Xử lý audit khi Hibernate insert một entity mới.
     *
     * Pseudo code:
     * 1. Nhận sự kiện insert từ Hibernate.
     * 2. Lấy entity vừa được tạo.
     * 3. Không có oldState vì đây là dữ liệu mới.
     * 4. Lấy newState từ event.
     * 5. Gọi processAudit với action mặc định là CREATE.
     */
    @Override
    public void onPostInsert(PostInsertEvent event) {
        processAudit(event.getEntity(), null, event.getState(), event.getPersister(), "CREATE");
    }

    /**
     * Xử lý audit khi Hibernate update một entity.
     *
     * Pseudo code:
     * 1. Nhận sự kiện update từ Hibernate.
     * 2. Lấy entity vừa được cập nhật.
     * 3. Lấy oldState trước khi cập nhật.
     * 4. Lấy newState sau khi cập nhật.
     * 5. Gọi processAudit với action mặc định là UPDATE.
     */
    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        processAudit(event.getEntity(), event.getOldState(), event.getState(), event.getPersister(), "UPDATE");
    }

    /**
     * Xử lý audit khi Hibernate delete một entity.
     *
     * Pseudo code:
     * 1. Nhận sự kiện delete từ Hibernate.
     * 2. Lấy entity vừa bị xóa.
     * 3. Lấy deletedState làm oldState.
     * 4. Không có newState vì entity đã bị xóa.
     * 5. Gọi processAudit với action mặc định là DELETE.
     */
    @Override
    public void onPostDelete(PostDeleteEvent event) {
        processAudit(event.getEntity(), event.getDeletedState(), null, event.getPersister(), "DELETE");
    }

    /**
     * Cho Hibernate biết listener này không cần xử lý sau khi transaction commit.
     *
     * Pseudo code:
     * 1. Nhận persister của entity.
     * 2. Trả về false vì audit event được publish trong post insert, post update hoặc post delete.
     */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    /**
     * Xử lý logic chính để tạo audit event khi entity thay đổi.
     * Method này dùng chung cho insert, update và delete để tránh lặp code.
     *
     * Pseudo code:
     * 1. Nếu entity là AuditLog thì bỏ qua để tránh audit chính bảng audit log.
     * 2. Lấy AuditContext hiện tại từ AuditContextHolder.
     * 3. Bỏ qua các entity được xử lý riêng hoặc không cần audit tự động.
     * 4. Nếu entity là RfidCard và action hiện tại là CREATE thì bỏ qua bulk import.
     * 5. Nếu không có AuditContext thì dừng xử lý.
     * 6. Đánh dấu context là đã có database modification.
     * 7. Lấy danh sách property name của entity từ Hibernate persister.
     * 8. Chuyển oldState và newState thành JSON.
     * 9. Lấy entity id bằng method getId nếu entity có method này.
     * 10. Xác định action từ AuditContext hoặc dùng defaultAction.
     * 11. Clone AuditContext để dữ liệu audit event không bị ảnh hưởng sau khi request kết thúc.
     * 12. Tạo AuditLogEvent chứa thông tin entity thay đổi.
     * 13. Publish AuditLogEvent để listener khác lưu audit log.
     * 14. Nếu có lỗi thì ghi log lỗi.
     */
    private void processAudit(Object entity, Object[] oldState, Object[] newState, EntityPersister persister, String defaultAction) {
        if (entity instanceof AuditLog) {
            return;
        }

        AuditContext context = AuditContextHolder.getContext();

        if (entity instanceof com.pbms.modules.infrastructure.domain.RoutingRule ||
                entity instanceof com.pbms.modules.finance.domain.PricingPolicy ||
                entity instanceof com.pbms.modules.finance.domain.PricingShift ||
                entity instanceof com.pbms.modules.finance.domain.PricingBlock ||
                entity instanceof com.pbms.modules.infrastructure.domain.Floor ||
                entity instanceof com.pbms.modules.infrastructure.domain.Zone ||
                entity instanceof com.pbms.modules.infrastructure.domain.Gate ||
                entity instanceof com.pbms.modules.infrastructure.domain.Slot) {
            return;
        }

        if (entity instanceof com.pbms.modules.infrastructure.domain.RfidCard) {
            if (context != null && "CREATE".equals(context.getAction())) {
                return;
            }
        }

        if (context == null) {
            return;
        }

        context.setDbModified(true);

        try {
            String[] propertyNames = persister.getPropertyNames();
            String oldJson = toJson(oldState, propertyNames);
            String newJson = toJson(newState, propertyNames);

            Long entityId = null;
            try {
                java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
                entityId = (Long) getIdMethod.invoke(entity);
            } catch (Exception e) {
            }

            String action = context.getAction() != null ? context.getAction() : defaultAction;

            AuditContext clonedContext = AuditContext.builder()
                    .actor(context.getActor())
                    .action(action)
                    .resource(context.getResource())
                    .description(context.getDescription())
                    .ipAddress(context.getIpAddress())
                    .build();

            AuditLogEvent auditEvent = new AuditLogEvent(
                    this,
                    clonedContext,
                    entity.getClass().getSimpleName(),
                    entityId,
                    oldJson,
                    newJson
            );

            eventPublisher.publishEvent(auditEvent);

        } catch (Exception e) {
            log.error("Error creating audit log event: {}", e.getMessage(), e);
        }
    }

    /**
     * Chuyển trạng thái entity của Hibernate thành chuỗi JSON.
     * Method này xử lý cả field bình thường và field là entity liên kết.
     *
     * Pseudo code:
     * 1. Nếu state là null thì trả về null.
     * 2. Tạo map để lưu property name và value tương ứng.
     * 3. Duyệt qua từng phần tử trong state.
     * 4. Nếu value là entity liên kết thì lấy id của entity đó.
     * 5. Nếu lấy được id thì lưu dạng EntityName(id=value).
     * 6. Nếu không lấy được id thì lưu value.toString().
     * 7. Nếu value không phải entity thì lưu trực tiếp vào map.
     * 8. Chuyển map thành JSON bằng ObjectMapper.
     * 9. Nếu serialize lỗi thì trả về JSON báo lỗi.
     */
    private String toJson(Object[] state, String[] propertyNames) {
        if (state == null) return null;
        try {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < state.length; i++) {
                Object value = state[i];
                if (value != null && isEntity(value)) {
                    try {
                        java.lang.reflect.Method getIdMethod = value.getClass().getMethod("getId");
                        Object id = getIdMethod.invoke(value);
                        map.put(propertyNames[i], value.getClass().getSimpleName() + "(id=" + id + ")");
                    } catch (Exception e) {
                        map.put(propertyNames[i], value.toString());
                    }
                } else {
                    map.put(propertyNames[i], value);
                }
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to serialize entity state: {}", e.getMessage());
            return "{\"error\": \"Unserializable state\"}";
        }
    }

    /**
     * Kiểm tra một object có phải là JPA entity hay không.
     *
     * Pseudo code:
     * 1. Lấy class của object.
     * 2. Kiểm tra class có annotation @Entity không.
     * 3. Nếu có thì trả về true.
     * 4. Nếu không thì trả về false.
     */
    private boolean isEntity(Object obj) {
        return obj.getClass().isAnnotationPresent(jakarta.persistence.Entity.class);
    }
}