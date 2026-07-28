/**
 * @Author: Thái Tân Phú 
 * @Date: 2026-07-06
 * @Description: REST API for Monthly Tickets.
 * @Dependencies: 
 * - MonthlyTicketService
 */
package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.MonthlyTicketDTO;
import com.pbms.modules.operation.service.MonthlyTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pbms.modules.system.service.SystemConfigService;
import com.pbms.modules.system.repository.SystemConfigRepository;
import com.pbms.modules.system.domain.SystemConfig;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operation/monthly-tickets")
@RequiredArgsConstructor
public class MonthlyTicketController {

    private final MonthlyTicketService monthlyTicketService;
    private final SystemConfigService systemConfigService;
    private final SystemConfigRepository configRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MonthlyTicketDTO>>> getAllTickets() {
        return ResponseEntity.ok(ApiResponse.success(
                monthlyTicketService.getAllTickets(),
                "Fetched monthly tickets successfully"
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MonthlyTicketDTO>> createTicket(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    monthlyTicketService.createTicket(payload),
                    "Monthly ticket created successfully"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
        }
    }

    @PutMapping("/{id}/renew")
    public ResponseEntity<ApiResponse<MonthlyTicketDTO>> renewTicket(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        try {
            int duration = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString()) : 1;
            return ResponseEntity.ok(ApiResponse.success(
                    monthlyTicketService.renewTicket(id, duration),
                    "Monthly ticket renewed successfully"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
        }
    }

    @GetMapping("/config-threshold")
    public ResponseEntity<ApiResponse<Integer>> getThreshold() {
        try {
            int threshold = 90; // Default
            String configVal = systemConfigService.getConfigByKey("MONTHLY_TICKET_ALERT_THRESHOLD").getConfigValue();
            if (configVal != null && !configVal.isBlank()) {
                threshold = Integer.parseInt(configVal);
            }
            return ResponseEntity.ok(ApiResponse.success(threshold, "Threshold fetched successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(90, "Fallback to default threshold"));
        }
    }

    @PostMapping("/config-threshold")
    public ResponseEntity<ApiResponse<Void>> setThreshold(@RequestBody Map<String, Integer> payload) {
        Integer threshold = payload.get("threshold");
        if (threshold == null) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Threshold required"));
        
        try {
            SystemConfig config = configRepo.findByConfigKey("MONTHLY_TICKET_ALERT_THRESHOLD")
                    .orElseGet(() -> {
                        SystemConfig c = new SystemConfig();
                        c.setConfigKey("MONTHLY_TICKET_ALERT_THRESHOLD");
                        return c;
                    });
            config.setConfigValue(String.valueOf(threshold));
            configRepo.save(config);
            return ResponseEntity.ok(ApiResponse.success(null, "Threshold updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Failed to update threshold"));
        }
    }
}
