package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.IotSlotUpdateRequest;
import com.pbms.modules.operation.service.IotIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/iot")
@RequiredArgsConstructor
public class IotIntegrationController {

    private final IotIntegrationService iotIntegrationService;

    @PostMapping("/slots/status")
    public ResponseEntity<ApiResponse<String>> updateSlotStatus(@RequestBody IotSlotUpdateRequest request) {
        try {
            iotIntegrationService.updateSlotStatus(request);
            return ResponseEntity.ok(ApiResponse.success("Success", "Slot characters are also available"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Error: " + e.getMessage()));
        }
    }
}

