/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-05
 * @Description: REST controller for managing Zone operations. Exposes endpoints for frontend map rendering.
 * @Dependencies:
 * - ZoneService (com.pbms.modules.infrastructure.service.ZoneService)
 * - ApiResponse (com.pbms.common.dto.ApiResponse)
 */
package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.dto.ZoneDTO;
import com.pbms.modules.infrastructure.service.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RestController
@RequestMapping("/api/v1/infrastructure/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    /**
     * @Function: getMapZones
     * @Description: Endpoint to retrieve all map zones with soft-deleted zones filtered out.
     * @Logic_Steps:
     * 1. Call ZoneService to fetch mapped ZoneDTOs.
     * 2. Wrap result in ApiResponse and return with HTTP 200 OK.
     * 
     * @returns {ResponseEntity<ApiResponse<List<ZoneDTO>>>} Standard API response containing the list of zones
     */
    @GetMapping("/map")
    public ResponseEntity<ApiResponse<List<ZoneDTO>>> getMapZones() {
        return ResponseEntity.ok(ApiResponse.success(zoneService.getMapZones(), "Fetched map zones"));
    }
}
