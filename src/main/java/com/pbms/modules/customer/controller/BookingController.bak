package com.pbms.modules.customer.controller;

import com.pbms.modules.customer.dto.BookingDTO;
import com.pbms.modules.operation.entity.ParkingSession;
import com.pbms.modules.operation.entity.Slot;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.repository.SlotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final ParkingSessionRepository sessionRepository;
    private final SlotRepository slotRepository;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ResponseEntity<?> createBooking(@RequestBody BookingDTO dto) {
        // Find slot by name since UI sends P-12
        Slot slot = slotRepository.findAll().stream()
                .filter(s -> dto.getSlotId().equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!"EMPTY".equals(slot.getStatus())) {
            throw new RuntimeException("Slot is not available");
        }

        slot.setStatus("BOOKED");
        slotRepository.save(slot);

        ParkingSession session = new ParkingSession();
        session.setPlateNumber("PRE-BOOKED"); // Will be updated on actual check-in
        session.setAllocatedSlot(slot);
        session.setStatus("BOOKED");
        session.setTimeIn(dto.getArrivalTime() != null ? dto.getArrivalTime() : LocalDateTime.now());
        // TTL is 30 mins from arrival time
        session.setExpiryTime(session.getTimeIn().plusMinutes(30));

        sessionRepository.save(session);

        return ResponseEntity.ok(session);
    }
}
