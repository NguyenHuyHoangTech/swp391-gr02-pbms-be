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

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<List<ZoneDTO>>> getMapZones() {
        return ResponseEntity.ok(ApiResponse.success(zoneService.getMapZones(), "Fetched map zones"));
    }
}

