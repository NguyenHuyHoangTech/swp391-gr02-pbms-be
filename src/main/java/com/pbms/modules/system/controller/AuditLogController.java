package com.pbms.modules.system.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.system.dto.AuditLogDTO;
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
    public ResponseEntity<ApiResponse<List<AuditLogDTO>>> getRecentLogs() {
        return ResponseEntity.ok(ApiResponse.success(
                auditLogService.getRecentLogs(),
                "Retrieve audit log list"
        ));
    }
}

