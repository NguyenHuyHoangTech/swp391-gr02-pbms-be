package com.pbms.modules.identity.service;

import com.pbms.modules.identity.domain.StaffWorkSession;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.infrastructure.domain.Gate;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.repository.StaffWorkSessionRepository;
import com.pbms.modules.finance.repository.TransactionRepository;
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
    private final TransactionRepository transactionRepository;

    @Transactional
    public StaffWorkSession startSession(String email, Long gateId, String gateType) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff do not agree"));

        // Check if staff already has an active session
        Optional<StaffWorkSession> existing = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");
        if (existing.isPresent()) {
            throw new IllegalStateException("You're the one who's going to leave the house." 
                + existing.get().getGate().getGateName() + ")e I'm happy to see my mother's song");
        }

        Gate gate;
        if (gateId != null && gateId == -1L && "PATROL".equals(gateType)) {
            // Auto-Pooling Logic
            List<Gate> patrolGates = gateRepository.findByGateType("PATROL");
            Gate availableGate = null;
            for (Gate g : patrolGates) {
                if (workSessionRepository.findByGateIdAndStatus(g.getId(), "ACTIVE").isEmpty()) {
                    availableGate = g;
                    break;
                }
            }
            if (availableGate == null) {
                availableGate = Gate.builder()
                        .gateName("Đi Tuần - Thiết bị " + (patrolGates.size() + 1))
                        .gateType("PATROL")
                        .status("INACTIVE")
                        .build();
                availableGate = gateRepository.save(availableGate);
            } else if ("DELETED".equals(availableGate.getStatus())) {
                availableGate.setStatus("INACTIVE");
                availableGate = gateRepository.save(availableGate);
            }
            gate = availableGate;
        } else {
            gate = gateRepository.findById(gateId)
                    .orElseThrow(() -> new IllegalArgumentException("Non-advisors:" + gateId));

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

        // Calculate expected revenue from preview BEFORE changing status to COMPLETED
        Map<String, Object> preview = getPreviewSettlement(email);
        BigDecimal expectedRevenue = preview.get("totalRevenue") != null 
            ? new BigDecimal(preview.get("totalRevenue").toString()) 
            : BigDecimal.ZERO;
            
        BigDecimal expectedCashRevenue = preview.get("cashRevenue") != null 
            ? new BigDecimal(preview.get("cashRevenue").toString()) 
            : BigDecimal.ZERO;
            
        BigDecimal expectedOtherRevenue = preview.get("otherRevenue") != null 
            ? new BigDecimal(preview.get("otherRevenue").toString()) 
            : BigDecimal.ZERO;
            
        session.setStatus("COMPLETED");
        session.setLogoutTime(com.pbms.common.utils.TimeProvider.now());
        
        session.setExpectedRevenue(expectedRevenue);
        session.setExpectedCashRevenue(expectedCashRevenue);
        session.setExpectedOtherRevenue(expectedOtherRevenue);
        session.setActualRevenue(declaredCash);
        
        // Variance is compared against Expected Cash, NOT Expected Total!
        BigDecimal variance = declaredCash.subtract(expectedCashRevenue);
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
        BigDecimal cashRevenue = BigDecimal.ZERO;
        BigDecimal otherRevenue = BigDecimal.ZERO;
        long totalTransactions = 0;

        if ("IN".equals(session.getGate().getGateType()) || "ENTRY".equals(session.getGate().getGateType())) {
            totalTransactions = checkIns.size();
        } else if ("OUT".equals(session.getGate().getGateType()) || "EXIT".equals(session.getGate().getGateType()) || "IN_OUT".equals(session.getGate().getGateType()) || "ENTRY_EXIT".equals(session.getGate().getGateType()) || "PATROL".equals(session.getGate().getGateType())) {
            
            if ("OUT".equals(session.getGate().getGateType()) || "EXIT".equals(session.getGate().getGateType())) {
                totalTransactions = checkOuts.size();
            } else {
                totalTransactions = checkIns.size() + checkOuts.size();
            }
        }
        
        List<com.pbms.modules.finance.domain.Transaction> transactions = transactionRepository.findByWorkSessionIdAndStatus(session.getId(), "SUCCESS");
        for (com.pbms.modules.finance.domain.Transaction t : transactions) {
            if (t.getAmount() != null) {
                totalRevenue = totalRevenue.add(t.getAmount());
                if ("CASH".equalsIgnoreCase(t.getPaymentMethod())) {
                    cashRevenue = cashRevenue.add(t.getAmount());
                } else {
                    otherRevenue = otherRevenue.add(t.getAmount());
                }
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
        preview.put("cashRevenue", cashRevenue);
        preview.put("otherRevenue", otherRevenue);
        return preview;
    }

    /**
     * [BE_FN_004] Lấy lịch sử đối soát ca trực của nhân viên trực cổng (Shift Reconciliation History).
     * - API Endpoint: GET /api/v1/work-sessions/history
     * - Chức năng: Truy vấn thông tin các ca trực đã hoàn thành (COMPLETED) của nhân viên trong khoảng thời gian 
     *   và lọc theo trạng thái cổng (gateType). Kết quả được phân trang và sắp xếp theo thời gian logout giảm dần.
     * - Các phương thức repository sử dụng:
     *   1. findByStatusAndLogoutTimeBetweenAndGateGateType (Lọc theo thời gian & loại cổng)
     *   2. findByStatusAndLogoutTimeBetween (Lọc theo thời gian)
     *   3. findByStatusAndGateGateType (Lọc theo loại cổng)
     *   4. findByStatus (Truy vấn mặc định theo ca hoàn thành)
     *
     * @param startDateStr Ngày bắt đầu lọc.
     * @param endDateStr Ngày kết thúc lọc.
     * @param gateType Loại cổng (ENTRY, EXIT, v.v.).
     * @param pageable Đối tượng cấu hình phân trang.
     * @return Trang (Page) chứa danh sách bản đồ thông tin ca trực chi tiết.
     */
    public Page<Map<String, Object>> getWorkSessionHistory(String startDateStr, String endDateStr, String gateType, Pageable pageable) {
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
            if (gateType != null && !gateType.isEmpty()) {
                sessions = workSessionRepository.findByStatusAndLogoutTimeBetweenAndGateGateType("COMPLETED", startDate, endDate, gateType, pageable);
            } else {
                sessions = workSessionRepository.findByStatusAndLogoutTimeBetween("COMPLETED", startDate, endDate, pageable);
            }
        } else {
            if (gateType != null && !gateType.isEmpty()) {
                sessions = workSessionRepository.findByStatusAndGateGateType("COMPLETED", gateType, pageable);
            } else {
                sessions = workSessionRepository.findByStatus("COMPLETED", pageable);
            }
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
            map.put("expectedCashRevenue", session.getExpectedCashRevenue());
            map.put("expectedOtherRevenue", session.getExpectedOtherRevenue());
            map.put("actualRevenue", session.getActualRevenue());
            map.put("revenueVariance", session.getRevenueVariance());
            map.put("discrepancyStatus", session.getDiscrepancyStatus());
            map.put("varianceReason", session.getVarianceReason());
            return map;
        });
    }
}

