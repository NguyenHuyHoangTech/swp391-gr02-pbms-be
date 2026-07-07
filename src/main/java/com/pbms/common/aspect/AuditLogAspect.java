package com.pbms.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbms.common.annotation.LogAudit;
import com.pbms.common.context.AuditContext;
import com.pbms.common.context.AuditContextHolder;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * Chức năng của file:
 * AuditLogAspect là lớp AOP dùng để bắt các method được đánh dấu bằng @LogAudit.
 * File này tạo AuditContext trước khi method thật chạy để lưu thông tin tạm thời
 * về người thực hiện, hành động, tài nguyên, mô tả và địa chỉ IP.
 *
 * Luồng audit log trong package common.aspect:
 * - AuditLogAspect tạo AuditContext và lưu vào AuditContextHolder.
 * - HibernateAuditListener đọc AuditContext khi Hibernate phát hiện entity bị insert, update hoặc delete.
 * - HibernateListenerConfig đăng ký HibernateAuditListener vào Hibernate lifecycle.
 * - Nếu HibernateAuditListener không ghi nhận thay đổi database, AuditLogAspect sẽ tự ghi log dạng Non-DB Action.
 *
 * Liên quan backend:
 * - LogAudit dùng để đánh dấu method cần ghi audit log.
 * - AuditContext lưu thông tin audit tạm thời trong lúc request đang chạy.
 * - AuditContextHolder giữ AuditContext theo từng thread/request.
 * - UserRepository dùng để tìm user đang thực hiện hành động.
 * - AuditLogRepository dùng để lưu audit log vào bảng audit_logs.
 * - AuditLog là entity đại diện cho một dòng lịch sử thao tác.
 *
 * Pseudo code:
 * 1. Bắt method có annotation @LogAudit.
 * 2. Lấy user hiện tại từ SecurityContextHolder.
 * 3. Lấy IP của request hiện tại.
 * 4. Tạo AuditContext chứa actor, action, resource, description và ipAddress.
 * 5. Đưa AuditContext vào AuditContextHolder.
 * 6. Cho method thật tiếp tục chạy.
 * 7. Nếu HibernateAuditListener bắt được thay đổi DB thì listener sẽ xử lý audit log database action.
 * 8. Nếu không có thay đổi DB thì AuditLogAspect tự ghi log dạng Non-DB Action.
 * 9. Xóa AuditContext sau khi xử lý xong để tránh ảnh hưởng request khác.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /**
     * Bao quanh method có @LogAudit để chuẩn bị AuditContext và ghi audit log khi cần.
     * Method này chạy cả trước và sau method thật, nên phù hợp để tạo context trước khi Hibernate xử lý entity.
     *
     * Pseudo code:
     * 1. Khởi tạo AuditContext ban đầu là null.
     * 2. Lấy Authentication hiện tại từ SecurityContextHolder.
     * 3. Nếu user đã đăng nhập thì tìm User trong database bằng email.
     * 4. Lấy địa chỉ IP từ request hiện tại nếu request tồn tại.
     * 5. Xác định resource từ @LogAudit hoặc từ tên class chứa method.
     * 6. Tạo AuditContext chứa actor, action, resource, description, ipAddress và dbModified=false.
     * 7. Lưu AuditContext vào AuditContextHolder.
     * 8. Cho method thật chạy bằng joinPoint.proceed().
     * 9. Sau khi method chạy xong, kiểm tra context có dbModified hay chưa.
     * 10. Nếu dbModified=true thì HibernateAuditListener đã ghi nhận thay đổi database.
     * 11. Nếu dbModified=false thì serialize tham số của method để làm request payload.
     * 12. Tạo AuditLog cho hành động Non-DB Action hoặc Batch Update.
     * 13. Lưu AuditLog vào database.
     * 14. Trả về kết quả của method thật.
     * 15. Luôn clear AuditContext trong finally.
     */
    @Around("@annotation(logAudit)")
    public Object logAround(ProceedingJoinPoint joinPoint, LogAudit logAudit) throws Throwable {
        AuditContext context = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User actor = null;
            if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                actor = userRepository.findByEmail(auth.getName()).orElse(null);
            }

            String ipAddress = "";
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ipAddress = request.getRemoteAddr();
            }

            String resource = logAudit.resource().isEmpty() ? joinPoint.getSignature().getDeclaringTypeName() : logAudit.resource();

            context = AuditContext.builder()
                    .actor(actor)
                    .action(logAudit.action())
                    .resource(resource)
                    .description(logAudit.description())
                    .ipAddress(ipAddress)
                    .dbModified(false)
                    .build();

            AuditContextHolder.setContext(context);

            Object result = joinPoint.proceed();

            if (!context.isDbModified()) {
                String requestPayload;
                try {
                    Object[] args = Arrays.stream(joinPoint.getArgs())
                            .filter(arg -> !(arg instanceof jakarta.servlet.http.HttpServletRequest ||
                                    arg instanceof jakarta.servlet.http.HttpServletResponse ||
                                    arg instanceof org.springframework.security.core.Authentication ||
                                    arg instanceof org.springframework.web.multipart.MultipartFile))
                            .toArray();
                    requestPayload = objectMapper.writeValueAsString(args.length == 1 ? args[0] : args);
                } catch (Exception e) {
                    requestPayload = Arrays.toString(joinPoint.getArgs());
                }

                AuditLog auditLog = AuditLog.builder()
                        .actor(context.getActor())
                        .action(context.getAction())
                        .resource(context.getResource() +
                                ("RoutingRule".equals(context.getResource()) ||
                                        "PricingPolicy".equals(context.getResource()) ||
                                        "MapConfiguration".equals(context.getResource()) ||
                                        "RfidCard".equals(context.getResource())
                                        ? " (Batch Update)" : " (Non-DB Action)"))
                        .description(context.getDescription())
                        .ipAddress(context.getIpAddress())
                        .oldValue(context.getOldValue())
                        .newValue(context.getNewValue() != null ? context.getNewValue() : requestPayload)
                        .build();

                auditLogRepository.save(auditLog);
            }

            return result;

        } catch (Throwable e) {
            throw e;
        } finally {
            AuditContextHolder.clearContext();
        }
    }
}