package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.dto.AuditLogDTO;
import com.pbms.modules.system.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getRecentLogs() {
        return auditLogRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
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

