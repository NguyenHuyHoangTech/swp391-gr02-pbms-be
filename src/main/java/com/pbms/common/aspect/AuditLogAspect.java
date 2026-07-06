package com.pbms.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbms.common.annotation.LogAudit;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * Aspect dùng để tự động ghi audit log cho các method được đánh dấu bằng @LogAudit.
 * Khi một method có @LogAudit thực thi thành công, class này sẽ lấy thông tin người thực hiện,
 * hành động, tài nguyên, dữ liệu đầu vào và địa chỉ IP rồi lưu vào bảng audit log.
 *
 * Pseudo code:
 * 1. Bắt các method có annotation @LogAudit.
 * 2. Sau khi method chạy thành công, lấy thông tin cần ghi log.
 * 3. Tạo đối tượng AuditLog.
 * 4. Lưu AuditLog vào database.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Tự động ghi audit log sau khi method có @LogAudit thực thi thành công.
     *
     * Pseudo code:
     * 1. Lấy user hiện tại đang thực hiện hành động.
     * 2. Lấy action, resource và description từ @LogAudit.
     * 3. Chuyển dữ liệu đầu vào của method thành JSON.
     * 4. Lấy địa chỉ IP của client.
     * 5. Tạo AuditLog.
     * 6. Lưu AuditLog vào database.
     * 7. Nếu có lỗi khi lưu log thì ghi lỗi ra console/log file.
     */
    @AfterReturning(pointcut = "@annotation(logAudit)", returning = "result")
    public void logAfter(JoinPoint joinPoint, LogAudit logAudit, Object result) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .actor(getCurrentActor())
                    .action(logAudit.action())
                    .resource(resolveResource(joinPoint, logAudit))
                    .description(logAudit.description())
                    .newValue(serializeArguments(joinPoint))
                    .ipAddress(getClientIpAddress())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Lấy thông tin người dùng hiện tại đang thực hiện hành động.
     *
     * Pseudo code:
     * 1. Lấy Authentication từ SecurityContextHolder.
     * 2. Nếu chưa đăng nhập hoặc là anonymousUser thì trả về null.
     * 3. Lấy email của user từ Authentication.
     * 4. Tìm user trong database bằng email.
     * 5. Nếu tìm thấy thì trả về User, nếu không thì trả về null.
     */
    private User getCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return null;
        }

        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    /**
     * Xác định tài nguyên bị tác động từ @LogAudit hoặc từ tên class chứa method.
     *
     * Pseudo code:
     * 1. Kiểm tra resource trong @LogAudit.
     * 2. Nếu resource có giá trị thì dùng resource đó.
     * 3. Nếu resource bị rỗng thì lấy tên class chứa method đang chạy.
     * 4. Trả về resource cuối cùng để lưu vào audit log.
     */
    private String resolveResource(JoinPoint joinPoint, LogAudit logAudit) {
        if (!logAudit.resource().isBlank()) {
            return logAudit.resource();
        }

        return joinPoint.getSignature().getDeclaringTypeName();
    }

    /**
     * Lấy địa chỉ IP của client thực hiện request.
     *
     * Pseudo code:
     * 1. Lấy request hiện tại từ RequestContextHolder.
     * 2. Nếu không có request thì trả về chuỗi rỗng.
     * 3. Kiểm tra header X-Forwarded-For.
     * 4. Nếu header tồn tại thì lấy IP đầu tiên trong header.
     * 5. Nếu không có header thì lấy IP trực tiếp từ request.getRemoteAddr().
     */
    private String getClientIpAddress() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return "";
        }

        HttpServletRequest request = attributes.getRequest();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Chuyển dữ liệu đầu vào của method thành chuỗi JSON để lưu vào audit log.
     *
     * Pseudo code:
     * 1. Lấy danh sách tham số của method.
     * 2. Lọc bỏ các tham số không phù hợp để serialize.
     * 3. Nếu chỉ còn một tham số thì serialize tham số đó.
     * 4. Nếu còn nhiều tham số thì serialize cả mảng tham số.
     * 5. Nếu serialize lỗi thì chuyển tham số sang dạng chuỗi bằng Arrays.toString().
     */
    private String serializeArguments(JoinPoint joinPoint) {
        Object[] safeArgs = getSerializableArguments(joinPoint.getArgs());

        try {
            Object value = safeArgs.length == 1 ? safeArgs[0] : safeArgs;
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize audit log argument: {}", e.getMessage());
            return Arrays.toString(safeArgs);
        }
    }

    /**
     * Lọc bỏ các tham số không phù hợp để ghi log như request, response, authentication và file upload.
     *
     * Pseudo code:
     * 1. Duyệt qua danh sách tham số của method.
     * 2. Bỏ qua HttpServletRequest.
     * 3. Bỏ qua HttpServletResponse.
     * 4. Bỏ qua Authentication.
     * 5. Bỏ qua MultipartFile.
     * 6. Trả về danh sách tham số còn lại.
     */
    private Object[] getSerializableArguments(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest))
                .filter(arg -> !(arg instanceof HttpServletResponse))
                .filter(arg -> !(arg instanceof Authentication))
                .filter(arg -> !(arg instanceof MultipartFile))
                .toArray();
    }
}