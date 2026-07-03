package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.service.ZoneService;
import com.pbms.modules.system.domain.BuildingProfile;
import com.pbms.modules.system.service.BuildingProfileService;
import com.pbms.modules.operation.service.VehicleTypeService;
import com.pbms.modules.infrastructure.dto.config.VehicleTypeDTO;
import com.pbms.modules.system.service.SystemConfigService;
import com.pbms.modules.system.domain.SystemConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final ZoneService zoneService;
    private final BuildingProfileService buildingProfileService;
    private final VehicleTypeService vehicleTypeService;
    private final SystemConfigService systemConfigService;

    @GetMapping("/time-offset")
    public ResponseEntity<ApiResponse<Long>> getTimeOffset() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("TIME_SIMULATED_OFFSET_SECONDS");
            long offset = Long.parseLong(config.getConfigValue());
            return ResponseEntity.ok(ApiResponse.success(offset, "This is a challenge for a long time."));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(0L, "Don't pay attention to the mood over time"));
        }
    }

    @GetMapping("/vehicle-types")
    public ResponseEntity<ApiResponse<List<VehicleTypeDTO>>> getVehicleTypes(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(vehicleTypeService.getAllVehicleTypes(activeOnly), "Vehicle types retrieved successfully"));
    }

    @GetMapping("/building-profile")
    public ResponseEntity<ApiResponse<BuildingProfile>> getBuildingProfile() {
        return ResponseEntity.ok(ApiResponse.success(buildingProfileService.getProfile(), "Let's get information from the house as well"));
    }

    @GetMapping("/parking-status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getParkingStatus() {
        List<Map<String, Object>> statusList = new ArrayList<>();
        
        List<VehicleTypeDTO> vehicleTypes = vehicleTypeService.getAllVehicleTypes(false);
        List<com.pbms.modules.infrastructure.dto.ZoneDTO> zones = zoneService.getMapZones();
        
        for (VehicleTypeDTO type : vehicleTypes) {
            int available = 0;
            for (com.pbms.modules.infrastructure.dto.ZoneDTO zone : zones) {
                if (zone.getVehicleTypeId() != null && zone.getVehicleTypeId().equals(type.getId())
                        && "WALK_IN".equals(zone.getFunctionType())) {
                    available += zone.getAvailableSlots();
                }
            }
            Map<String, Object> map = new HashMap<>();
            map.put("type", type.getCategory()); 
            map.put("label", type.getTypeName());
            map.put("available", available);
            statusList.add(map);
        }

        return ResponseEntity.ok(ApiResponse.success(statusList, "Fraudulently deleting the car"));
    }
}

