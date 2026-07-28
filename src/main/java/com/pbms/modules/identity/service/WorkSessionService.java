/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ NGHIỆP VỤ (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO DỊCH VỤ VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Ký hiệu @Service báo cho Spring Boot biết class này chứa Logic lõi. 
 *   Spring sẽ khởi tạo nó thành Singleton Bean.
 * - Minh chứng 2: Dùng @RequiredArgsConstructor để tự động tiêm các Repository vào Service.
 * 
 * BƯỚC 2: BẢO ĐẢM TOÀN VẸN GIAO DỊCH (TRANSACTION MANAGEMENT)
 * - Minh chứng: Các hàm thay đổi dữ liệu được gắn @Transactional. Điều này đảm bảo 
 *   khi có lỗi xảy ra, toàn bộ thao tác DB sẽ được Rollback, không gây rác dữ liệu.
 * 
 * BƯỚC 3: THỰC THI LOGIC NGHIỆP VỤ
 * - Minh chứng: Gọi các hàm từ Repository (như indById, save) để tương tác 
 *   trực tiếp với CSDL, xử lý các ngoại lệ (Exception) và trả về DTO cho Controller.
 * 
 * @author Phạm Anh Tuấn
 * @created 03/05/2026
 */
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
    /**
     * =========================================================================
     * NGHIỆP VỤ: STARTSESSION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho startSession.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
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

        Gate gate = gateRepository.findById(gateId)
                .orElseThrow(() -> new IllegalArgumentException("Gate not found: " + gateId));

        // Check if gate is already taken by an active session
        Optional<StaffWorkSession> gateExisting = workSessionRepository
                .findByGateIdAndStatus(gate.getId(), "ACTIVE");
        if (gateExisting.isPresent()) {
            throw new IllegalStateException("Gate is already occupied by " 
                + gateExisting.get().getStaff().getFullName());
        }

        StaffWorkSession session = StaffWorkSession.builder()
                .staff(staff)
                .gate(gate)
                .workGateType(gateType != null ? gateType : "IN_OUT")
                .loginTime(com.pbms.common.utils.TimeProvider.now())
                .status("ACTIVE")
                .build();

        return workSessionRepository.save(session);
    }

    @Transactional
    /**
     * =========================================================================
     * NGHIỆP VỤ: ENDSESSION
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho endSession.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public Map<String, Object> endSession(String email) {
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

        // Reset physical gate type back to generic ENTRY_EXIT after shift ends
        Gate gate = session.getGate();
        if (gate != null && session.getWorkGateType() != null && !session.getWorkGateType().equals("PATROL")) {
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

        String workGateType = session.getWorkGateType();
        Map<String, Object> preview = new HashMap<>();
        if ("IN".equals(workGateType) || "ENTRY".equals(workGateType)) {
            preview.put("action", "Check-in processing required");
        } else if ("OUT".equals(workGateType) || "EXIT".equals(workGateType) || "IN_OUT".equals(workGateType) || "ENTRY_EXIT".equals(workGateType) || "PATROL".equals(workGateType)) {
            preview.put("action", "Checkout processing required");
            if ("OUT".equals(workGateType) || "EXIT".equals(workGateType)) {
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

        preview.put("hasActiveSession", true);
        preview.put("sessionId", session.getId());
        preview.put("gateId", session.getGate().getId());
        preview.put("gateType", session.getWorkGateType());
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
     * =========================================================================
     * NGHIỆP VỤ: GETWORKSESSIONHISTORY
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho getWorkSessionHistory.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
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
                sessions = workSessionRepository.findByStatusAndLogoutTimeBetweenAndWorkGateType("COMPLETED", startDate, endDate, gateType, pageable);
            } else {
                sessions = workSessionRepository.findByStatusAndLogoutTimeBetween("COMPLETED", startDate, endDate, pageable);
            }
        } else {
            if (gateType != null && !gateType.isEmpty()) {
                sessions = workSessionRepository.findByStatusAndWorkGateType("COMPLETED", gateType, pageable);
            } else {
                sessions = workSessionRepository.findByStatus("COMPLETED", pageable);
            }
        }

        return sessions.map(session -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", session.getId());
            map.put("staffName", session.getStaff().getFullName());
            map.put("gateName", session.getGate().getGateName());
            map.put("gateType", session.getWorkGateType());
            map.put("loginTime", session.getLoginTime());
            map.put("logoutTime", session.getLogoutTime());
            map.put("expectedRevenue", session.getExpectedRevenue());
            map.put("expectedCashRevenue", session.getExpectedCashRevenue());
            map.put("expectedOtherRevenue", session.getExpectedOtherRevenue());
            map.put("status", session.getStatus());
            return map;
        });
    }
}
