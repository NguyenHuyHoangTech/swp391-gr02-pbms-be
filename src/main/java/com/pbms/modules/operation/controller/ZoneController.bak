package com.pbms.modules.operation.controller;

import com.pbms.modules.operation.dto.ZoneLayoutDTO;
import com.pbms.modules.operation.service.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    public ResponseEntity<?> getAllZones() {
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    @PutMapping("/{id}/layout")
    public ResponseEntity<?> updateLayout(@PathVariable Long id, @RequestBody ZoneLayoutDTO dto) {
        return ResponseEntity.ok(zoneService.updateZoneLayout(id, dto));
    }
}
