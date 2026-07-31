// Author: Võ Trung Hiếu
package com.pbms.modules.system.controller;

import com.pbms.modules.system.service.DataMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/system/migration")
@RequiredArgsConstructor
public class DataMigrationController {
    
    private final DataMigrationService migrationService;

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> runMigration() {
        migrationService.runMigration();
        return ResponseEntity.ok(ApiResponse.success(null, "Migration triggered successfully"));
    }
}
