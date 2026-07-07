/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-06
 * @Description: Controller for retrieving and updating map configurations.
 * @Dependencies: MapConfigurationService
 */
package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.config.MapConfigDTO;
import com.pbms.modules.infrastructure.service.MapConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.pbms.common.annotation.LogAudit;

@RestController
@RequestMapping("/api/v1/infrastructure/map")
@RequiredArgsConstructor
@Slf4j
public class MapConfigurationController {

    private final MapConfigurationService mapConfigurationService;

    /**
     * @Function: getMapConfig
     * @Description: Endpoint to retrieve the current map configuration.
     * @Logic_Steps:
     * 1. Call MapConfigurationService to gather the configuration data.
     * 2. Wrap the result in an ApiResponse payload and return.
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<MapConfigDTO>> getMapConfig() {
        return ResponseEntity.ok(ApiResponse.success(
                mapConfigurationService.getMapConfiguration(),
                "Successfully retrieved parking configuration"
        ));
    }

    /**
     * @Function: saveMapConfig
     * @Description: Endpoint to save or update the map configuration.
     * @Logic_Steps:
     * 1. Receive the MapConfigDTO payload from the client.
     * 2. Call MapConfigurationService to persist the updates into the database.
     * 3. Handle exceptions gracefully if parsing or business rules fail.
     */
    @PostMapping("/save")
    @LogAudit(action = "UPDATE", resource = "MapConfiguration", description = "Update space map configuration")
    public ResponseEntity<ApiResponse<String>> saveMapConfig(@RequestBody MapConfigDTO mapConfigDTO) {
        try {
            mapConfigurationService.saveMapConfiguration(mapConfigDTO);
            return ResponseEntity.ok(ApiResponse.success("Successfully updated map configuration", "Success"));
        } catch (Exception e) {
            log.error("Failed to update map configuration", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
