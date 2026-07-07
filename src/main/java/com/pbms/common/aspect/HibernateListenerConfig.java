package com.pbms.common.aspect;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

/**
 * Chức năng của file:
 * HibernateListenerConfig là file cấu hình dùng để đăng ký HibernateAuditListener vào Hibernate.
 * File này giúp hệ thống tự động bắt các sự kiện insert, update và delete của entity trong database.
 *
 * Luồng audit log trong package common.aspect:
 * - AuditLogAspect tạo AuditContext khi method có @LogAudit được gọi.
 * - HibernateListenerConfig đăng ký HibernateAuditListener vào Hibernate lifecycle.
 * - HibernateAuditListener được gọi tự động khi Hibernate insert, update hoặc delete entity.
 * - HibernateAuditListener tạo AuditLogEvent để ghi nhận lịch sử thao tác.
 *
 * Liên quan backend:
 * - EntityManagerFactory dùng để truy cập cấu hình Hibernate bên dưới JPA.
 * - SessionFactoryImplementor cho phép lấy service registry của Hibernate.
 * - EventListenerRegistry dùng để gắn listener vào các event của Hibernate.
 * - HibernateAuditListener xử lý logic audit khi entity thay đổi.
 *
 * Pseudo code:
 * 1. Khai báo đây là class cấu hình của Spring.
 * 2. Lấy EntityManagerFactory từ JPA.
 * 3. Inject HibernateAuditListener vào config.
 * 4. Sau khi bean được tạo, lấy SessionFactory của Hibernate.
 * 5. Lấy EventListenerRegistry từ SessionFactory.
 * 6. Đăng ký HibernateAuditListener vào event POST_INSERT.
 * 7. Đăng ký HibernateAuditListener vào event POST_UPDATE.
 * 8. Đăng ký HibernateAuditListener vào event POST_DELETE.
 */
@Configuration
@RequiredArgsConstructor
public class HibernateListenerConfig {

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private final HibernateAuditListener hibernateAuditListener;

    /**
     * Đăng ký HibernateAuditListener vào các sự kiện thay đổi entity của Hibernate.
     * Method này chạy sau khi Spring khởi tạo bean nhờ @PostConstruct.
     *
     * Pseudo code:
     * 1. Chuyển EntityManagerFactory thành SessionFactoryImplementor của Hibernate.
     * 2. Lấy EventListenerRegistry từ service registry của Hibernate.
     * 3. Gắn HibernateAuditListener vào sự kiện POST_INSERT.
     * 4. Gắn HibernateAuditListener vào sự kiện POST_UPDATE.
     * 5. Gắn HibernateAuditListener vào sự kiện POST_DELETE.
     * 6. Sau khi đăng ký xong, Hibernate sẽ tự gọi listener khi entity thay đổi.
     */
    @PostConstruct
    public void registerListeners() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

        registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(hibernateAuditListener);
        registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(hibernateAuditListener);
        registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(hibernateAuditListener);
    }
}