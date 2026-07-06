package com.pbms.modules.identity.service;

import com.pbms.modules.identity.domain.StaffWorkSession;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.infrastructure.domain.Gate;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.incident.repository.IncidentTicketRepository;
import com.pbms.modules.operation.repository.StaffWorkSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkSessionService {

    private final StaffWorkSessionRepository workSessionRepository;
    private final UserRepository userRepository;
    private final GateRepository gateRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final IncidentTicketRepository incidentTicketRepository;

    @Transactional
    public StaffWorkSession startSession(String email, Long gateId, String gateType) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff do not agree"));

        Gate gate = gateRepository.findById(gateId)
                .orElseThrow(() -> new IllegalArgumentException("Non-advisors:" + gateId));

        // Check if staff already has an active session
        Optional<StaffWorkSession> existing = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");
        if (existing.isPresent()) {
            throw new IllegalStateException("You're the one who's going to leave the house." 
                + existing.get().getGate().getGateName() + ")e I'm happy to see my mother's song");
        }

        // Check if gate is already taken by an active session
        Optional<StaffWorkSession> gateExisting = workSessionRepository
                .findByGateIdAndStatus(gate.getId(), "ACTIVE");
        if (gateExisting.isPresent()) {
            throw new IllegalStateException("These people are wrong." 
                + gateExisting.get().getStaff().getFullName() + "I'm afraid that these staff members are not theirs.");
        }

        // Update physical gate type if requested by staff to temporarily lock it for this shift
        if (gateType != null && !gateType.trim().isEmpty() && !gateType.equals("PATROL")) {
            gate.setGateType(gateType);
            gate.setStatus("ACTIVE");
            gateRepository.save(gate);
        }

        StaffWorkSession session = StaffWorkSession.builder()
                .staff(staff)
                .gate(gate)
                .loginTime(com.pbms.common.utils.TimeProvider.now())
                .status("ACTIVE")
                .build();

        return workSessionRepository.save(session);
    }

    @Transactional
    public Map<String, Object> endSession(String email, BigDecimal declaredCash, String varianceReason) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff do not agree"));

        Optional<StaffWorkSession> sessionOpt = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");
                
        if (sessionOpt.isEmpty()) {
            // Graceful fallback to unstick frontend if local storage is desynced
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", null);
            result.put("staffName", staff.getFullName());
            result.put("gateName", "N/A");
            result.put("message", "Work session checked in successfully");
            return result;
        }

        StaffWorkSession session = sessionOpt.get();
        session.setStatus("COMPLETED");
        session.setLogoutTime(com.pbms.common.utils.TimeProvider.now());

        // Calculate expected revenue from preview
        Map<String, Object> preview = getPreviewSettlement(email);
        BigDecimal expectedRevenue = preview.get("totalRevenue") != null 
            ? new BigDecimal(preview.get("totalRevenue").toString()) 
            : BigDecimal.ZERO;
        
        session.setExpectedRevenue(expectedRevenue);
        session.setActualRevenue(declaredCash);
        
        BigDecimal variance = declaredCash.subtract(expectedRevenue);
        session.setRevenueVariance(variance);
        
        String status = "MATCH";
        if (variance.compareTo(BigDecimal.ZERO) < 0) {
            status = "SHORT";
        } else if (variance.compareTo(BigDecimal.ZERO) > 0) {
            status = "OVER";
        }
        session.setDiscrepancyStatus(status);
        session.setVarianceReason(varianceReason);

        // Reset physical gate type back to generic ENTRY_EXIT after shift ends
        Gate gate = session.getGate();
        if (gate != null && !"PATROL".equals(gate.getGateType())) {
            gate.setGateType("ENTRY_EXIT");
            gate.setStatus("INACTIVE");
            gateRepository.save(gate);
        }

        workSessionRepository.save(session);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("staffName", staff.getFullName());
        result.put("gateName", session.getGate().getGateName());
        result.put("loginTime", session.getLoginTime());
        result.put("logoutTime", session.getLogoutTime());
        result.put("declaredCash", declaredCash);
        result.put("message", "Work session checked out successfully");
        return result;
    }

    /**
     * Preview the current shift's expected revenue before checking out.
     * Calculates total expected revenue based on the gate type and the number of checkout sessions.
     * Includes a specific edge case for PATROL staff who collect penalty fees directly.
     * @param email Staff's email
     * @return Map containing preview data (expected revenue, transaction count, etc.)
     */
    public Map<String, Object> getPreviewSettlement(String email) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff do not agree"));

        Optional<StaffWorkSession> sessionOpt = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");

        if (sessionOpt.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("hasActiveSession", false);
            return empty;
        }

        StaffWorkSession session = sessionOpt.get();

        List<ParkingSession> checkIns = parkingSessionRepository
                .findByGateInIdAndTimeInBetween(
                        session.getGate().getId(),
                        session.getLoginTime(),
                        com.pbms.common.utils.TimeProvider.now()
                );

        List<ParkingSession> checkOuts = parkingSessionRepository
                .findByGateOutIdAndTimeOutBetween(
                        session.getGate().getId(),
                        session.getLoginTime(),
                        com.pbms.common.utils.TimeProvider.now()
                );

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalTransactions = 0;

        if ("IN".equals(session.getGate().getGateType()) || "ENTRY".equals(session.getGate().getGateType())) {
            totalTransactions = checkIns.size();
            totalRevenue = BigDecimal.ZERO;
        } else if ("OUT".equals(session.getGate().getGateType()) || "EXIT".equals(session.getGate().getGateType())) {
            totalTransactions = checkOuts.size();
            totalRevenue = checkOuts.stream()
                    .map(ps -> ps.getTotalFee() != null ? ps.getTotalFee() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        } else {
            // IN_OUT or PATROL
            totalTransactions = checkIns.size() + checkOuts.size();
            totalRevenue = checkOuts.stream()
                    .map(ps -> ps.getTotalFee() != null ? ps.getTotalFee() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
                    
            // [CRITICAL FIX]: Account for Patrol penalty collections.
            // Since PATROL gates don't have "check-outs", we query IncidentTickets directly 
            // to count the cash they collected on the floor.
            if ("PATROL".equals(session.getGate().getGateType())) {
                List<com.pbms.modules.incident.domain.IncidentTicket> resolvedTickets = incidentTicketRepository
                        .findByUserIdAndResolvedAtBetweenAndStatus(
                                staff.getId(),
                                session.getLoginTime(),
                                com.pbms.common.utils.TimeProvider.now(),
                                "RESOLVED"
                        );
                
                BigDecimal patrolRevenue = resolvedTickets.stream()
                        .map(t -> t.getFineAmount() != null ? t.getFineAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
                        
                totalRevenue = totalRevenue.add(patrolRevenue);
                totalTransactions += resolvedTickets.size();
            }
        }

        Map<String, Object> preview = new HashMap<>();
        preview.put("hasActiveSession", true);
        preview.put("sessionId", session.getId());
        preview.put("gateId", session.getGate().getId());
        preview.put("gateType", session.getGate().getGateType());
        preview.put("staffName", staff.getFullName());
        preview.put("gateName", session.getGate().getGateName());
        preview.put("loginTime", session.getLoginTime());
        preview.put("totalTransactions", totalTransactions);
        preview.put("totalRevenue", totalRevenue);
        return preview;
    }

    public Page<Map<String, Object>> getWorkSessionHistory(String startDateStr, String endDateStr, Pageable pageable) {
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        if (startDateStr != null && !startDateStr.isEmpty()) {
            startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
        }
        if (endDateStr != null && !endDateStr.isEmpty()) {
            endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
        }

        Page<StaffWorkSession> sessions;
        if (startDate != null && endDate != null) {
            sessions = workSessionRepository.findByStatusAndLogoutTimeBetween("COMPLETED", startDate, endDate, pageable);
        } else {
            sessions = workSessionRepository.findByStatus("COMPLETED", pageable);
        }

        return sessions.map(session -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", session.getId());
            map.put("staffName", session.getStaff().getFullName());
            map.put("gateName", session.getGate().getGateName());
            map.put("gateType", session.getGate().getGateType());
            map.put("loginTime", session.getLoginTime());
            map.put("logoutTime", session.getLogoutTime());
            map.put("expectedRevenue", session.getExpectedRevenue());
            map.put("actualRevenue", session.getActualRevenue());
            map.put("revenueVariance", session.getRevenueVariance());
            map.put("discrepancyStatus", session.getDiscrepancyStatus());
            map.put("varianceReason", session.getVarianceReason());
            return map;
        });
    }
}

