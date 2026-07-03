package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.RoutingRuleDTO;
import com.pbms.modules.infrastructure.service.RoutingRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/routing-rules")
@RequiredArgsConstructor
public class RoutingRuleController {

    private final RoutingRuleService routingRuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutingRuleDTO>>> getRoutingRules(
            @RequestParam(defaultValue = "CAR") String vehicleType,
            @RequestParam(required = false) Long floorId) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.getRoutingRulesByVehicleTypeAndFloor(vehicleType, floorId),
                "Success"
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<List<RoutingRuleDTO>>> updateRoutingRules(@RequestBody RoutingRuleDTO.BatchUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.updateRoutingRules(request),
                "Routing rules updated successfully"
        ));
    }
}

