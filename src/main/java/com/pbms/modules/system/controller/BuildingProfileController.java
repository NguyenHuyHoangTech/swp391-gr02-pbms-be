package com.pbms.modules.system.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.system.domain.BuildingProfile;
import com.pbms.modules.system.service.BuildingProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;

@RestController
@RequestMapping("/api/v1/system/building-profile")
@PreAuthorize("hasRole('MANAGER')")
public class BuildingProfileController {

    private final BuildingProfileService service;

    public BuildingProfileController(BuildingProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BuildingProfile>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(service.getProfile(), "Profile fetched successfully"));
    }

    @PutMapping
    @PreAuthorize("hasRole('MANAGER')")
    @LogAudit(action = "UPDATE", resource = "BuildingProfile", description = "Update building profile")
    public ResponseEntity<ApiResponse<BuildingProfile>> updateProfile(@jakarta.validation.Valid @RequestBody BuildingProfile profile) {
        BuildingProfile updated = service.updateProfile(profile);
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated successfully"));
    }
}

