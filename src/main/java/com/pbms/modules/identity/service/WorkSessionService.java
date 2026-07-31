/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Service managing the staff duty shift lifecycle (open shift, close shift)
 *               and the revenue settlement figures used to reconcile cash handovers.
 * @Dependencies:
 * - StaffWorkSession (com.pbms.modules.identity.domain.StaffWorkSession)
 * - StaffWorkSessionRepository (com.pbms.modules.operation.repository.StaffWorkSessionRepository)
 * - UserRepository (com.pbms.modules.identity.repository.UserRepository)
 * - GateRepository (com.pbms.modules.infrastructure.repository.GateRepository)
 * - ParkingSessionRepository (com.pbms.modules.operation.repository.ParkingSessionRepository)
 * - TransactionRepository (com.pbms.modules.finance.repository.TransactionRepository)
 */
package com.pbms.modules.identity.service;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ REPOSITORY LÕI (IDENTITY & OPERATION & FINANCE)
// =========================================================================
import com.pbms.modules.identity.domain.StaffWorkSession; // Entity ca trực của nhân viên tại cổng.
import com.pbms.modules.identity.domain.User; // Entity tài khoản người dùng (ở đây là nhân viên trực).
import com.pbms.modules.identity.repository.UserRepository; // Tra cứu nhân viên theo email lấy từ JWT.
import com.pbms.modules.infrastructure.domain.Gate; // Entity cổng kiểm soát vật lý.
import com.pbms.modules.infrastructure.repository.GateRepository; // Đọc/ghi trạng thái Cổng khi mở/đóng ca.
import com.pbms.modules.operation.domain.ParkingSession; // Entity 1 lượt xe vào/ra bãi.
import com.pbms.modules.operation.repository.ParkingSessionRepository; // Đếm số lượt xe vào/ra phát sinh trong ca.
import com.pbms.modules.operation.repository.StaffWorkSessionRepository; // Kho dữ liệu ca trực.
import com.pbms.modules.finance.repository.TransactionRepository; // Tổng hợp giao dịch thành công để chốt doanh thu.

// =========================================================================
// PHẦN 2: CÁC THƯ VIỆN SPRING BOOT / LOMBOK VÀ PHÂN TRANG
// =========================================================================
import lombok.RequiredArgsConstructor; // Lombok tự sinh Constructor cho các field "final" bên dưới.
import org.springframework.stereotype.Service; // Đánh dấu đây là tầng nghiệp vụ, Spring quản lý làm 1 Bean.
import org.springframework.transaction.annotation.Transactional; // Nhiều thao tác DB trong 1 hàm: thành công hết hoặc rollback hết.
import org.springframework.data.domain.Page; // 1 "trang" kết quả kèm tổng số bản ghi.
import org.springframework.data.domain.Pageable; // Tham số phân trang (trang số mấy, mỗi trang bao nhiêu dòng).

// =========================================================================
// PHẦN 3: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal; // Kiểu số thập phân chính xác cao, dùng cho tiền tệ tránh sai số float.
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * =========================================================================================
 * DỊCH VỤ QUẢN LÝ CA TRỰC NHÂN VIÊN VÀ ĐỐI SOÁT DOANH THU (WORK SESSION SERVICE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Chịu trách nhiệm quản lý vòng đời ca làm việc của Nhân viên kiểm soát cổng (Mở ca,
 * Đóng ca), thống kê trước số lượng giao dịch và doanh thu (tiền mặt/chuyển khoản),
 * cũng như phục vụ truy vấn lịch sử ca trực cho Quản lý bãi xe.
 *
 * VAI TRÒ CỔNG THUỘC VỀ CA TRỰC, KHÔNG THUỘC VỀ CỔNG:
 * Nhân viên khai báo vai trò lúc mở ca (`workGateType` = ENTRY/EXIT, mặc định
 * IN_OUT nếu không khai báo). Cổng vật lý là chốt trung tính, không mang sẵn loại
 * vào/ra — nhờ vậy cùng 1 chốt có thể trực chiều vào buổi sáng, chiều ra buổi chiều.
 * Giá trị này quyết định cách đếm số lượt giao dịch khi chốt ca (xem
 * `getPreviewSettlement`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Bắt buộc kiểm tra tính độc quyền của ca trực (1 nhân viên chỉ được
 *   trực 1 cổng tại 1 thời điểm, và 1 cổng chỉ nhận 1 nhân viên ACTIVE).
 * - Minh chứng 2: Doanh thu chốt ca được tổng hợp động từ bảng `transactions` với
 *   trạng thái `SUCCESS`, chia rõ doanh thu tiền mặt (`CASH`) và chuyển khoản — hệ
 *   thống tự tính thay vì tin vào số tiền Frontend khai báo, tránh sai lệch/gian lận.
 * - Minh chứng 3: Trạng thái Cổng (`OCCUPIED`/`IDLE`) được cập nhật đối xứng ở
 *   `startSession()` và `endSession()`, nhờ đó Sơ đồ bãi xe luôn tô đúng màu cổng
 *   đang có người trực mà không cần 1 cơ chế đồng bộ riêng.
 * =========================================================================================
 */
@Service
@RequiredArgsConstructor
public class WorkSessionService {

    // Kho dữ liệu ca trực — nguồn dữ liệu chính của toàn bộ class.
    private final StaffWorkSessionRepository workSessionRepository;

    // Tra cứu nhân viên theo email (email lấy từ JWT của Spring Security).
    private final UserRepository userRepository;

    // Dùng để cập nhật trạng thái Cổng sang OCCUPIED/IDLE khi mở/đóng ca.
    private final GateRepository gateRepository;

    // Dùng để đếm số lượt xe vào/ra phát sinh trong khoảng thời gian ca trực.
    private final ParkingSessionRepository parkingSessionRepository;

    // Dùng để tổng hợp doanh thu thực tế đã thu trong ca.
    private final TransactionRepository transactionRepository;

    /**
     * =========================================================================
     * API/HÀM: MỞ CA TRỰC TẠI CỔNG (START SESSION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Kiểm tra ràng buộc độc quyền ca trực của nhân viên và cổng, sau đó khởi
     * tạo bản ghi `StaffWorkSession` với trạng thái "ACTIVE".
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Kiểm tra email nhân viên trong DB.
     * 2. Nếu nhân viên này đang có ca trực "ACTIVE" tại cổng khác -> Ném ngoại lệ.
     * 3. Kiểm tra cổng (Gate) theo ID.
     * 4. Nếu cổng này đang được nhân viên khác trực "ACTIVE" -> Ném ngoại lệ.
     * 5. Khởi tạo đối tượng ca trực với thời gian đăng nhập hiện tại; nếu Client
     *    không gửi vai trò cổng thì mặc định "IN_OUT" (trực cả 2 chiều).
     * 6. Đưa trạng thái Cổng sang "OCCUPIED" để đối xứng với `endSession()`
     *    (đưa về "IDLE" khi đóng ca) — nhờ đó Sơ đồ bãi xe tô đúng màu Cổng
     *    đang có người trực.
     * 7. Lưu ca trực mới vào DB.
     */
    @Transactional
    public StaffWorkSession startSession(String email, Long gateId, String gateType) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff account not found"));

        // KIỂM TRA ĐỘC QUYỀN NHÂN VIÊN: Một nhân viên không được trực 2 cổng cùng lúc
        Optional<StaffWorkSession> existing = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");
        if (existing.isPresent()) {
            throw new IllegalStateException("You are already on duty at gate \""
                    + existing.get().getGate().getGateName() + "\". Please end that shift first.");
        }

        Gate gate = gateRepository.findById(gateId)
                .orElseThrow(() -> new IllegalArgumentException("Gate not found: " + gateId));

        // KIỂM TRA ĐỘC QUYỀN CỔNG: Một cổng không được nhận 2 nhân viên trực cùng lúc
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

        // Đưa trạng thái Cổng sang "OCCUPIED" vì đã có người trực. Bắt buộc phải
        // ghi ở đây để đối xứng với endSession(): Sơ đồ bãi xe suy màu Cổng từ
        // chính ca trực ACTIVE, nên thiếu bước này Cổng sẽ không bao giờ đổi màu.
        gate.setStatus("OCCUPIED");
        gateRepository.save(gate);

        return workSessionRepository.save(session);
    }

    /**
     * =========================================================================
     * API/HÀM: ĐÓNG CA TRỰC VÀ CHỐT DOANH THU (END SESSION)
     * =========================================================================
     * MỤC ĐÍCH:
     * Kết thúc ca làm việc của nhân viên, tổng hợp doanh thu tiền mặt và chuyển
     * khoản từ bảng giao dịch, đổi trạng thái ca sang "COMPLETED".
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Tìm phiên làm việc "ACTIVE" của nhân viên theo email.
     * 2. Nếu không có phiên -> Trả về kết quả dự phòng an toàn để giải phóng
     *    giao diện Frontend (tránh nhân viên bị kẹt không mở được ca mới).
     * 3. Gọi `getPreviewSettlement(email)` TRƯỚC KHI cập nhật trạng thái COMPLETED
     *    để tính tổng doanh thu (`totalRevenue`, `cashRevenue`, `otherRevenue`).
     * 4. Gán thời gian đăng xuất, doanh thu chốt ca vào Entity và lưu DB.
     * 5. Đưa trạng thái Cổng về "IDLE" để chờ ca trực mới tiếp quản.
     */
    @Transactional
    public Map<String, Object> endSession(String email) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff account not found"));

        Optional<StaffWorkSession> sessionOpt = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");

        if (sessionOpt.isEmpty()) {
            // Xử lý an toàn: giải phóng giao diện Frontend nếu bộ nhớ tạm bị lệch
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", null);
            result.put("staffName", staff.getFullName());
            result.put("gateName", "N/A");
            result.put("message", "No active work session found to end");
            return result;
        }

        StaffWorkSession session = sessionOpt.get();

        // TÍNH TOÁN DOANH THU CHỐT CA: Bắt buộc tính trước khi đổi sang "COMPLETED",
        // vì getPreviewSettlement() chỉ tra được phiên đang ở trạng thái ACTIVE.
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

        // Trả trạng thái Cổng về "IDLE" (trống, chờ ca trực mới) sau khi đóng ca.
        // Dùng đúng bộ từ vựng của `GateConfigDTO` — IDLE/OCCUPIED/MAINTENANCE —
        // vì màn hình sơ đồ in thẳng chuỗi này ra cho quản lý đọc.
        Gate gate = session.getGate();
        if (gate != null) {
            gate.setStatus("IDLE");
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
     * =========================================================================
     * API/HÀM: XEM TRƯỚC BÁO CÁO DOANH THU CA TRỰC (PREVIEW SETTLEMENT)
     * =========================================================================
     * MỤC ĐÍCH:
     * Tính toán doanh thu tạm thời của ca trực hiện tại theo email nhân viên,
     * bao gồm tổng số lượt xe vào/ra, doanh thu tiền mặt (`CASH`), và doanh thu
     * chuyển khoản/hình thức khác.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Tìm phiên làm việc "ACTIVE" của nhân viên theo email.
     * 2. Nếu không có -> Trả về `hasActiveSession = false`.
     * 3. Truy vấn danh sách xe vào (`checkIns`) và xe ra (`checkOuts`) tại cổng,
     *    giới hạn trong khoảng từ lúc mở ca tới thời điểm hiện tại.
     * 4. Xác định tổng số lượt giao dịch theo vai trò cổng của ca trực:
     *    - ENTRY/IN -> chỉ đếm lượt xe VÀO.
     *    - EXIT/OUT -> chỉ đếm lượt xe RA.
     *    - IN_OUT/ENTRY_EXIT -> đếm cả 2 chiều.
     * 5. Duyệt qua tất cả giao dịch "SUCCESS" của phiên làm việc:
     *    - Nếu `paymentMethod == CASH` -> Cộng vào `cashRevenue`.
     *    - Ngược lại -> Cộng vào `otherRevenue`.
     * 6. Trả về map chứa đầy đủ thông số báo cáo ca làm việc.
     */
    public Map<String, Object> getPreviewSettlement(String email) {
        User staff = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff account not found"));

        Optional<StaffWorkSession> sessionOpt = workSessionRepository
                .findByStaffIdAndStatus(staff.getId(), "ACTIVE");

        if (sessionOpt.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("hasActiveSession", false);
            return empty;
        }

        StaffWorkSession session = sessionOpt.get();

        // Lấy danh sách lượt xe vào/ra trong khoảng thời gian từ lúc mở ca đến hiện tại
        List<ParkingSession> checkIns = parkingSessionRepository
                .findByGateInIdAndTimeInBetween(
                        session.getGate().getId(),
                        session.getLoginTime(),
                        com.pbms.common.utils.TimeProvider.now());

        List<ParkingSession> checkOuts = parkingSessionRepository
                .findByGateOutIdAndTimeOutBetween(
                        session.getGate().getId(),
                        session.getLoginTime(),
                        com.pbms.common.utils.TimeProvider.now());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal cashRevenue = BigDecimal.ZERO;
        BigDecimal otherRevenue = BigDecimal.ZERO;
        long totalTransactions = 0;

        String workGateType = session.getWorkGateType();
        Map<String, Object> preview = new HashMap<>();
        if ("IN".equals(workGateType) || "ENTRY".equals(workGateType)) {
            preview.put("action", "Check-in processing required");
            totalTransactions = checkIns.size();
        } else if ("OUT".equals(workGateType) || "EXIT".equals(workGateType) || "IN_OUT".equals(workGateType)
                || "ENTRY_EXIT".equals(workGateType)) {
            preview.put("action", "Checkout processing required");
            if ("OUT".equals(workGateType) || "EXIT".equals(workGateType)) {
                totalTransactions = checkOuts.size();
            } else {
                totalTransactions = checkIns.size() + checkOuts.size();
            }
        }

        // TỔNG HỢP DOANH THU THEO PHƯƠNG THỨC THANH TOÁN
        List<com.pbms.modules.finance.domain.Transaction> transactions = transactionRepository
                .findByWorkSessionIdAndStatus(session.getId(), "SUCCESS");
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
     * API/HÀM: TRA CỨU LỊCH SỬ CÁC CA TRỰC (GET WORK SESSION HISTORY)
     * =========================================================================
     * MỤC ĐÍCH:
     * Lọc danh sách các ca làm việc đã chốt (trạng thái "COMPLETED") theo khoảng
     * ngày (`startDate`, `endDate`) và vai trò cổng (`gateType`), phân trang cho UI.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Phân tích chuỗi ngày `startDateStr` (từ 00:00:00) và `endDateStr` (đến 23:59:59).
     * 2. Truy vấn DB theo tiêu chí kết hợp:
     *    - Có cả khoảng ngày + vai trò cổng.
     *    - Chỉ có khoảng ngày.
     *    - Chỉ có vai trò cổng.
     *    - Hoặc lấy toàn bộ danh sách "COMPLETED".
     * 3. Ánh xạ sang Map kết quả cho Frontend render bảng lịch sử.
     */
    public Page<Map<String, Object>> getWorkSessionHistory(String startDateStr, String endDateStr, String gateType,
            Pageable pageable) {
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
                sessions = workSessionRepository.findByStatusAndLogoutTimeBetweenAndWorkGateType("COMPLETED", startDate,
                        endDate, gateType, pageable);
            } else {
                sessions = workSessionRepository.findByStatusAndLogoutTimeBetween("COMPLETED", startDate, endDate,
                        pageable);
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
