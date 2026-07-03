package com.pbms.modules.operation.controller;

import com.pbms.modules.operation.dto.ParkingSessionDTO;
import com.pbms.modules.operation.service.ParkingSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/parking-sessions")
@RequiredArgsConstructor
public class ParkingSessionController {

    private final ParkingSessionService sessionService;

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody ParkingSessionDTO payload) {
        return ResponseEntity.ok(sessionService.checkIn(payload));
    }

    @PutMapping("/{sessionId}/check-out")
    public ResponseEntity<?> checkOut(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.checkOut(sessionId));
    }
}
