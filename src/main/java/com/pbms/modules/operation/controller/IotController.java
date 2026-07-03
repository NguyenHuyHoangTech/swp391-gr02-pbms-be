package com.pbms.modules.operation.controller;

import com.pbms.modules.operation.dto.CameraScanDTO;
import com.pbms.modules.operation.service.IotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/iot")
@RequiredArgsConstructor
public class IotController {

    private final IotService iotService;

    @PostMapping("/cameras/scan")
    public ResponseEntity<?> receiveCameraScan(@RequestBody CameraScanDTO payload) {
        iotService.broadcastCameraScan(payload);
        return ResponseEntity.ok().build();
    }
}

