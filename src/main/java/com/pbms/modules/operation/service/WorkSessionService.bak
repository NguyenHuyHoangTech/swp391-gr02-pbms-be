package com.pbms.modules.operation.service;

import com.pbms.modules.operation.dto.WorkSessionDTO;
import com.pbms.modules.operation.entity.ParkingSession;
import com.pbms.modules.operation.entity.StaffWorkSession;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.repository.StaffWorkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkSessionService {

    private final StaffWorkSessionRepository workSessionRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    private Long getCurrentStaffId() {
        // Mocking current logged in staff ID (from V1__Init_Tables.sql staff@pbms.com is id 3)
        return 3L;
    }

    @Transactional
    public StaffWorkSession startShift(WorkSessionDTO dto) {
        Long staffId = getCurrentStaffId();
        
        StaffWorkSession session = new StaffWorkSession();
        session.setStaffId(staffId);
        session.setGateId(dto.getGateId());
        session.setSettlementStatus("OPEN");
        
        return workSessionRepository.save(session);
    }

    public StaffWorkSession previewSettlement() {
        Long staffId = getCurrentStaffId();
        StaffWorkSession session = workSessionRepository.findTopByStaffIdOrderByStartedAtDesc(staffId)
                .orElseThrow(() -> new RuntimeException("No open shift found"));

        if (!"OPEN".equals(session.getSettlementStatus())) {
            throw new RuntimeException("Current shift is not open");
        }

        List<ParkingSession> sessions = parkingSessionRepository.findByCheckoutStaffIdAndTimeOutBetween(
                staffId, session.getStartedAt(), LocalDateTime.now());

        BigDecimal sysTotalCash = sessions.stream()
                .map(ParkingSession::getTotalFee)
                .filter(fee -> fee != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        session.setSysTotalCash(sysTotalCash);
        return session;
    }

    @Transactional
    public StaffWorkSession endShift(WorkSessionDTO dto) {
        StaffWorkSession session = previewSettlement(); // Gets latest open and updates sysTotalCash

        session.setEndedAt(LocalDateTime.now());
        session.setDeclaredCash(dto.getDeclaredCash());
        
        BigDecimal variance = dto.getDeclaredCash().subtract(session.getSysTotalCash());
        session.setVarianceAmount(variance);
        session.setVarianceReason(dto.getVarianceReason());

        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            session.setSettlementStatus("BALANCED");
        } else if (variance.compareTo(BigDecimal.ZERO) < 0) {
            session.setSettlementStatus("SHORTAGE");
        } else {
            session.setSettlementStatus("OVERAGE");
        }

        return workSessionRepository.save(session);
    }
}
