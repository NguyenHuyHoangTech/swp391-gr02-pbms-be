package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.config.VehicleTypeDTO;
import com.pbms.modules.operation.service.VehicleTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.pbms.common.service.FileStorageService;

import java.util.List;

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
    public ResponseEntity<ApiResponse<VehicleTypeDTO>> create(@RequestBody VehicleTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(service.createVehicleType(dto), "Created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleTypeDTO>> update(@PathVariable Long id, @RequestBody VehicleTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(service.updateVehicleType(id, dto), "Vehicle type updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.deleteVehicleType(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Deleted successfully"));
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

