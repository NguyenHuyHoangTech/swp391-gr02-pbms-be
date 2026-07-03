package com.pbms.modules.customer.controller;

import com.pbms.modules.operation.entity.ParkingSession;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parking-sessions")
@RequiredArgsConstructor
public class CustomerController {

    private final ParkingSessionRepository sessionRepository;

    @GetMapping("/my-active")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getMyActiveSession() {
        // Simplified for Phase 6: return the first ACTIVE or BOOKED session
        // In reality, this would filter by the logged-in user's registered vehicles
        List<ParkingSession> sessions = sessionRepository.findAll();
        ParkingSession activeSession = sessions.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) || "BOOKED".equals(s.getStatus()))
                .findFirst()
                .orElse(null);

        return ResponseEntity.ok(activeSession);
    }
}
