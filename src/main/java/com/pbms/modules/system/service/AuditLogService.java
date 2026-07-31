// Author: Võ Trung Hiếu
package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.dto.AuditLogDTO;
import com.pbms.modules.system.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getLogs(String action, String resource, String email, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findWithFilters(action, resource, email, startDate, endDate, pageable)
                .map(this::mapToDTO);
    }

    private AuditLogDTO mapToDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .action(log.getAction())
                .resource(log.getResource())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .actor(log.getActor() != null ? 
                       AuditLogDTO.ActorDTO.builder().email(log.getActor().getEmail()).build() : null)
                .build();
    }
}

