package com.pbms.modules.operation.controller;

import com.pbms.modules.operation.dto.WorkSessionDTO;
import com.pbms.modules.operation.service.WorkSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/work-sessions")
@RequiredArgsConstructor
public class WorkSessionController {

    private final WorkSessionService workSessionService;

    @PostMapping("/start")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<?> startShift(@RequestBody WorkSessionDTO dto) {
        return ResponseEntity.ok(workSessionService.startShift(dto));
    }

    @GetMapping("/current/preview-settlement")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<?> previewSettlement() {
        return ResponseEntity.ok(workSessionService.previewSettlement());
    }

    @PutMapping("/end")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<?> endShift(@RequestBody WorkSessionDTO dto) {
        return ResponseEntity.ok(workSessionService.endShift(dto));
    }
}
