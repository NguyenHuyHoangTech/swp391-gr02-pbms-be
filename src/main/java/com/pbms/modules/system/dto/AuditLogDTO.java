package com.pbms.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private String action;
    private String resource;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String description;
    private LocalDateTime createdAt;
    private ActorDTO actor;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorDTO {
        private String email;
    }
}
