package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.config.VehicleTypeDTO;
import com.pbms.modules.operation.service.VehicleTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;
import org.springframework.web.multipart.MultipartFile;
import com.pbms.common.service.FileStorageService;

import java.util.List;

/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-16
 * @Description: REST controller for managing vehicle types. Exposes endpoints for CRUD operations
 *               and icon uploads. Requires MANAGER role for modifications, and allows STAFF for viewing.
 * @Dependencies: VehicleTypeService, FileStorageService, ApiResponse, LogAudit
 */
@RestController
@RequestMapping("/api/v1/operation/vehicle-types")
@PreAuthorize("hasRole('MANAGER')")
public class VehicleTypeController {

    private final VehicleTypeService service;
    private final FileStorageService fileStorageService;

    public VehicleTypeController(VehicleTypeService service, FileStorageService fileStorageService) {
        this.service = service;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<VehicleTypeDTO>>> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(service.getAllVehicleTypes(activeOnly), "Fetched successfully"));
    }

    @PostMapping
    @LogAudit(action = "CREATE", resource = "VehicleType", description = "Create vehicle type")
    public ResponseEntity<ApiResponse<VehicleTypeDTO>> create(@RequestBody VehicleTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(service.createVehicleType(dto), "Created successfully"));
    }

    @PutMapping("/{id}")
    @LogAudit(action = "UPDATE", resource = "VehicleType", description = "Update vehicle type")
    public ResponseEntity<ApiResponse<VehicleTypeDTO>> update(@PathVariable Long id, @RequestBody VehicleTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(service.updateVehicleType(id, dto), "Vehicle type updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @LogAudit(action = "UPDATE", resource = "VehicleType", description = "Toggle vehicle type status (lock/unlock)")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@PathVariable Long id) {
        service.toggleVehicleTypeStatus(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Status updated successfully"));
    }

    @PostMapping("/{id}/icon")
    public ResponseEntity<ApiResponse<VehicleTypeDTO>> uploadIcon(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file);
        VehicleTypeDTO updated = service.updateIcon(id, fileUrl);
        return ResponseEntity.ok(ApiResponse.success(updated, "Icon uploaded successfully"));
    }
}

