package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.VehicleDTO;
import com.pbms.modules.operation.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operation/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleDTO>>> getAllVehicles() {
        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.getAllVehicles(),
                "Check out the list of useful products."
        ));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<VehicleDTO>> checkVehicleByPlate(@RequestParam String plate) {
        try {
            return ResponseEntity.ok(ApiResponse.success(vehicleService.getVehicleByPlate(plate), "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/blacklist")
    public ResponseEntity<ApiResponse<VehicleDTO>> blacklistVehicle(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        try {
            String reason = payload.get("reason");
            VehicleDTO dto = vehicleService.setBlacklist(id, reason);
            return ResponseEntity.ok(ApiResponse.success(dto, "Sign in to the list"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/blacklist-by-plate")
    public ResponseEntity<ApiResponse<VehicleDTO>> blacklistVehicleByPlate(
            @RequestBody Map<String, String> payload) {
        try {
            String plate = payload.get("plate");
            String reason = payload.get("reason");
            String evidenceUrl = payload.get("evidenceUrl");
            if (plate == null || plate.isBlank()) throw new IllegalArgumentException("Plate is required");
            VehicleDTO dto = vehicleService.setBlacklistByPlate(plate, reason, evidenceUrl);
            return ResponseEntity.ok(ApiResponse.success(dto, "Vehicle blacklisted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/unblacklist")
    public ResponseEntity<ApiResponse<VehicleDTO>> unblacklistVehicle(@PathVariable Long id) {
        try {
            VehicleDTO dto = vehicleService.removeBlacklist(id);
            return ResponseEntity.ok(ApiResponse.success(dto, "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
}

