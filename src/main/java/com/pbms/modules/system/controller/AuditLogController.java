package com.pbms.modules.system.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.system.domain.AuditLog;
import com.pbms.modules.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLog>>> getRecentLogs() {
        return ResponseEntity.ok(ApiResponse.success(
                auditLogService.getRecentLogs(),
                "Read the audit log list as well"
        ));
    }
}

