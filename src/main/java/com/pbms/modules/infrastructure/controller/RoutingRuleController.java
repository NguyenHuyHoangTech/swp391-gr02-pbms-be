/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-10
 * @Description: REST controller for configuring automatic zone-routing rules. Exposes endpoints
 *               consumed by the manager's Vehicle Routing screen to view and save the routing chain.
 * @Dependencies:
 * - RoutingRuleService (com.pbms.modules.infrastructure.service.RoutingRuleService)
 * - ApiResponse (com.pbms.common.dto.ApiResponse)
 */
package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.RoutingRuleDTO;
import com.pbms.modules.infrastructure.service.RoutingRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/routing-rules")
@RequiredArgsConstructor
public class RoutingRuleController {

    private final RoutingRuleService routingRuleService;

    /**
     * @Function: getRoutingRules
     * @Description: Endpoint to retrieve the configured routing-rule chains for a vehicle type.
     * @Logic_Steps:
     * 1. Read vehicleType (default CAR) and optional floorId from query params.
     * 2. Delegate to RoutingRuleService to build the timeframe chains.
     * 3. Wrap the result in an ApiResponse and return with HTTP 200 OK.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutingRuleDTO>>> getRoutingRules(
            @RequestParam(defaultValue = "CAR") String vehicleType,
            @RequestParam(required = false) Long floorId) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.getRoutingRulesByVehicleTypeAndFloor(vehicleType, floorId),
                "Success"
        ));
    }

    /**
     * @Function: updateRoutingRules
     * @Description: Endpoint to overwrite the routing-rule configuration for a vehicle type.
     * @Logic_Steps:
     * 1. Receive the BatchUpdateRequest payload (timeframes + zone chain) from the manager screen.
     * 2. Delegate to RoutingRuleService to soft-deactivate old rules and persist the new chain.
     * 3. Wrap the refreshed configuration in an ApiResponse and return with HTTP 200 OK.
     */
    @PutMapping
    @LogAudit(action = "UPDATE", resource = "RoutingRule", description = "Update routing rules")
    public ResponseEntity<ApiResponse<List<RoutingRuleDTO>>> updateRoutingRules(@RequestBody RoutingRuleDTO.BatchUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.updateRoutingRules(request),
                "Routing rules updated successfully"
        ));
    }
}
