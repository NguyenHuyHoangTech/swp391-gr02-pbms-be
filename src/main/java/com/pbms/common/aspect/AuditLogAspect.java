package com.pbms.common.aspect;

import com.pbms.common.annotation.LogAudit;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @AfterReturning(pointcut = "@annotation(logAudit)", returning = "result")
    public void logAfter(JoinPoint joinPoint, LogAudit logAudit, Object result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User actor = null;
            if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                // Find user by email (which is set as principal name in JWT filter)
                actor = userRepository.findByEmail(auth.getName()).orElse(null);
            }

            String ipAddress = "";
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ipAddress = request.getRemoteAddr();
            }

            String newValue;
            try {
                // Ignore HttpServletRequest/Response and other un-serializable objects
                Object[] args = Arrays.stream(joinPoint.getArgs())
                        .filter(arg -> !(arg instanceof jakarta.servlet.http.HttpServletRequest || 
                                         arg instanceof jakarta.servlet.http.HttpServletResponse ||
                                         arg instanceof org.springframework.security.core.Authentication ||
                                         arg instanceof org.springframework.web.multipart.MultipartFile))
                        .toArray();
                newValue = objectMapper.writeValueAsString(args.length == 1 ? args[0] : args);
            } catch (Exception e) {
                log.warn("Failed to serialize audit log argument: {}", e.getMessage());
                newValue = Arrays.toString(joinPoint.getArgs());
            }

            AuditLog auditLog = AuditLog.builder()
                    .actor(actor)
                    .action(logAudit.action())
                    .resource(logAudit.resource().isEmpty() ? joinPoint.getSignature().getDeclaringTypeName() : logAudit.resource())
                    .description(logAudit.description())
                    .newValue(newValue)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(auditLog);
            
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}

