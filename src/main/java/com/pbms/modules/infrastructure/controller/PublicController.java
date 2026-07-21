/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Các API không yêu cầu đăng nhập, dùng cho trang khách hàng
 * công khai (xem tình trạng bãi xe, thông tin toà nhà...) trước khi họ tạo
 * tài khoản hoặc đặt chỗ.
 * @Dependencies:
 * - ZoneService, BuildingProfileService, VehicleTypeService, SystemConfigService (Local)
 * - ReservationService (Local)
 */
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
import org.springframework.web.bind.annotation.PathVariable;

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
    private final com.pbms.modules.operation.service.ReservationService reservationService;

    @GetMapping("/debug/reservations/timers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDebugTimers() {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getDebugTimers(), "Fetched debug timers"));
    }

    @GetMapping("/time-offset")
    public ResponseEntity<ApiResponse<Long>> getTimeOffset() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("TIME_SIMULATED_OFFSET_SECONDS");
            long offset = Long.parseLong(config.getConfigValue());
            return ResponseEntity.ok(ApiResponse.success(offset, "Time offset fetched successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(0L, "No time offset configured, defaulting to 0"));
        }
    }

    @GetMapping("/vehicle-types")
    public ResponseEntity<ApiResponse<List<VehicleTypeDTO>>> getVehicleTypes(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(vehicleTypeService.getAllVehicleTypes(activeOnly), "Vehicle types retrieved successfully"));
    }

    @GetMapping("/building-profile")
    public ResponseEntity<ApiResponse<BuildingProfile>> getBuildingProfile() {
        return ResponseEntity.ok(ApiResponse.success(buildingProfileService.getProfile(), "Building profile fetched successfully"));
    }

    /**
     * @Function: getParkingStatus
     * @Description: Tổng số chỗ trống theo từng loại xe, chỉ tính zone
     * WALK_IN (khách vãng lai) - dùng cho trang chủ hiển thị "còn bao nhiêu
     * chỗ" mà không lộ chi tiết từng zone/slot cho người chưa đăng nhập.
     */
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

        return ResponseEntity.ok(ApiResponse.success(statusList, "Parking status fetched successfully"));
    }

    @GetMapping("/config/{key}")
    public ResponseEntity<ApiResponse<String>> getPublicConfig(@PathVariable String key) {
        if (key != null && key.startsWith("RESERVATION_")) {
            try {
                com.pbms.modules.system.domain.SystemConfig config = systemConfigService.getConfigByKey(key);
                if (config != null) {
                    return ResponseEntity.ok(ApiResponse.success(config.getConfigValue(), "Config retrieved"));
                } else {
                    return ResponseEntity.ok(ApiResponse.success(getDefaultValueForKey(key), "Default value"));
                }
            } catch (Exception e) {
                return ResponseEntity.ok(ApiResponse.success(getDefaultValueForKey(key), "Default value"));
            }
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "Config not public"));
    }

    private String getDefaultValueForKey(String key) {
        switch (key) {
            case "RESERVATION_EARLY_MINS": return "30";
            case "RESERVATION_DEFAULT_DURATION_MINS": return "120";
            case "RESERVATION_REFUND_EARLY_PERCENT": return "1.0";
            case "RESERVATION_REFUND_LATE_PERCENT": return "0.5";
            default: return "";
        }
    }
}
