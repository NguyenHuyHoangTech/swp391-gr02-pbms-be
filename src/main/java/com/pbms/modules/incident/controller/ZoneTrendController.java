package com.pbms.modules.incident.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.incident.dto.ZoneTrendDTO;
import com.pbms.modules.incident.service.ZoneTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/zone-trends")
@RequiredArgsConstructor
public class ZoneTrendController {

    private final ZoneTrendService zoneTrendService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneTrendDTO>>> getZoneTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        if (date == null) {
            date = LocalDate.now();
        }
        
        return ResponseEntity.ok(ApiResponse.success(
                zoneTrendService.getZoneTrends(date),
                "Success"
        ));
    }
}

