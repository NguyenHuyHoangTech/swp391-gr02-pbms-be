package com.pbms.modules.incident.service;

/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ SỰ CỐ CỦA HỆ THỐNG (KÈM MINH CHỨNG CODE)
 * (Trình bày chi tiết luồng nghiệp vụ tiếp nhận, duyệt 2 giai đoạn, phạt tiền và giải quyết sự cố)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO DỊCH VỤ VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Annotation `@Service` báo cho Spring Boot biết class này là một Service Bean 
 *   xử lý nghiệp vụ lõi. Spring sẽ khởi tạo nó thành Singleton Bean duy nhất lưu trên RAM.
 * - Minh chứng 2: Annotation `@RequiredArgsConstructor` của Lombok tự động sinh ra Constructor 
 *   chứa tất cả các trường dữ liệu `final` (từ dòng 30 đến dòng 43 như `IncidentTicketRepository`, 
 *   `ParkingSessionRepository`...). Đây là kỹ thuật Constructor Injection giúp tiêm các kho dữ liệu 
 *   và dịch vụ phụ trợ vào Service một cách an toàn, Thread-safe.
 * - Minh chứng 3: Annotation `@Slf4j` tự động tạo ra đối tượng logger `log` giúp ghi nhật ký hệ thống.
 * 
 * BƯỚC 2: TIẾP NHẬN YÊU CẦU VÀ TẠO SỰ CỐ GIAI ĐOẠN 1 (CREATE INCIDENT & PHASE 1)
 * - Minh chứng 1: Hàm `createIncident(...)` tiếp nhận DTO từ `IncidentTicketController`.
 * - Minh chứng 2: Hàm `verifyVehicleOwnership(...)` kiểm tra an ninh: Khách hàng chỉ được báo 
 *   sự cố cho xe chính chủ (miễn trừ cho Nhân viên/Quản lý).
 * - Minh chứng 3: Bảng `IncidentTicket` được lưu với trạng thái ban đầu "PENDING".
 * - Minh chứng 4: Hàm `processPhase1(...)` cho phép Bảo vệ/Quản lý duyệt hồ sơ Phase 1, bổ sung ảnh 
 *   bằng chứng, tính tiền phạt ban đầu và chuyển trạng thái ticket sang "WAITING_CHECKOUT".
 * 
 * BƯỚC 3: ĐỐI SOÁT BẢO MẬT VÀ QUY TRÌNH THANH TOÁN GIAI ĐOẠN 2 (RESOLVE PHASE 2 & JWT CHECKOUT TOKEN)
 * - Minh chứng 1: Hàm `resolveIncident(...)` xử lý quy trình Phase 2 khi khách thanh toán tại quầy.
 * - Minh chứng 2: Kiểm tra bảo mật 2 lớp (2-Layer Validation): Giải mã `checkoutToken` (JWT) từ 
 *   `GateOperationService`, đối soát `sessionId` và `expectedFee` trong Token với tổng số tiền 
 *   thu thực tế (`parkingFee + penaltyFee - discountAmount`). Ngăn chặn gian lận hoặc sai lệch giá tiền.
 * - Minh chứng 3: Ghi nhận giao dịch tài chính `Transaction` vào CSDL nếu tổng tiền > 0.
 * - Minh chứng 4: Đóng phiên đỗ xe (COMPLETED), giải phóng hoặc cập nhật trạng thái thẻ RFID 
 *   (AVAILABLE / LOST / DAMAGED) và đổi trạng thái sự cố sang "RESOLVED".
 * 
 * BƯỚC 4: XỬ LÝ HỦY VÀ PHỤC HỒI TRẠNG THÁI (CANCEL INCIDENT & ROLLBACK)
 * - Minh chứng 1: Hàm `cancelIncident(...)` hỗ trợ hủy sự cố nếu báo sai hoặc không đủ điều kiện.
 * - Minh chứng 2: Tự động phục hồi phiên đỗ xe về "ACTIVE", hoàn trả cờ thẻ RFID về "IN_USE" và 
 *   gỡ bỏ cờ Blacklist nếu hủy sự cố trốn phí (`BLACKLIST_VIOLATION`).
 * 
 * BƯỚC 5: TỰ ĐỘNG CRONJOB VÀ PHÁT SÓNG REAL-TIME (OVERSTAY CRONJOB & WEBSOCKET BROADCAST)
 * - Minh chứng 1: Hàm `@Scheduled(cron = "0 0 2 * * ?") public void handleOverstayVehicles()` tự động 
 *   quét các xe đỗ quá 72h (OVERSTAY) vào lúc 2:00 AM mỗi ngày và tự động tạo ticket cảnh báo.
 * - Minh chứng 2: Hàm `saveAndBroadcast(...)` dùng `SimpMessagingTemplate` phát sóng sự kiện 
 *   WebSocket tới kênh `/topic/alerts` để cập nhật giao diện hiển thị tức thì.
 * 
 * =========================================================================================
 * PHỤ LỤC: CÁC LUỒNG XỬ LÝ ĐẶC THÙ (LOST CARD, FEE ADJUSTMENT, FEE DISPUTE)
 * - Phụ lục 1: `createLostCardIncident(...)` - Nhân viên tạo nhanh sự cố báo mất thẻ và áp phí phạt.
 * - Phụ lục 2: `adjustFeeIncident(...)` - Quản lý can thiệp điều chỉnh trực tiếp phí đỗ xe.
 * - Phụ lục 3: `resolveFeeDispute(...)` - Giải quyết khiếu nại mức phí, áp dụng tiền giảm giá discount.
 * =========================================================================================
 */

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DOMAIN VÀ DTO NỘI BỘ
// Công dụng: Chứa các Entity bảng CSDL và DTO truyền nhận dữ liệu sự cố
// =========================================================================
import com.pbms.modules.incident.domain.IncidentTicket; // Entity bảng dbo.incident_tickets
import com.pbms.modules.incident.dto.IncidentTicketRequest; // DTO yêu cầu tạo sự cố từ Client
import com.pbms.modules.incident.dto.IncidentTicketDTO; // DTO dữ liệu sự cố trả về cho Client
import com.pbms.modules.incident.repository.IncidentTicketRepository; // Kho dữ liệu thao tác bảng incident_tickets
import com.pbms.modules.operation.domain.ParkingSession; // Entity phiên đỗ xe
import com.pbms.modules.infrastructure.domain.RfidCard; // Entity thẻ từ RFID
import com.pbms.modules.operation.repository.ParkingSessionRepository; // Kho dữ liệu thao tác bảng parking_sessions
import com.pbms.modules.infrastructure.repository.RfidCardRepository; // Kho dữ liệu thao tác bảng rfid_cards
import com.pbms.modules.infrastructure.repository.ZoneRepository; // Kho dữ liệu thao tác bảng zones

// =========================================================================
// PHẦN 2: CÁC LỚP REPOSITORY VÀ SERVICE PHỤ TRỢ
// Công dụng: Kết nối với các phân hệ Identity, System, Finance, Operation
// =========================================================================
import lombok.RequiredArgsConstructor; // Annotation tự động sinh Constructor Injection cho trường final
import lombok.extern.slf4j.Slf4j; // Annotation tự động sinh Logger log
import org.springframework.messaging.simp.SimpMessagingTemplate; // Công cụ bắn thông báo WebSocket Real-time
import org.springframework.scheduling.annotation.Scheduled; // Công cụ lập lịch chạy tác vụ tự động Cronjob
import org.springframework.stereotype.Service; // Đánh dấu lớp Service Bean
import org.springframework.transaction.annotation.Transactional; // Đảm bảo tính toàn vẹn giao dịch CSDL

// =========================================================================
// PHẦN 3: CÁC THƯ VIỆN XỬ LÝ THỜI GIAN VÀ TIỀN TỆ
// Công dụng: Tính toán thời gian đỗ, mức phạt và tiền đỗ xe
// =========================================================================
import java.math.BigDecimal; // Kiểu dữ liệu tiền tệ chính xác cao
import java.time.LocalDateTime; // Thư viện xử lý ngày giờ Java chuẩn
import java.util.List; // Thư viện danh sách List
import java.util.stream.Collectors; // Thư viện biến đổi danh sách Stream

/**
 * === PHẦN 4: ĐỊNH NGHĨA CLASS SERVICE LÕI QUẢN LÝ SỰ CỐ ===
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    // =========================================================================
    // NHÓM 1: CÁC KHO DỮ LIỆU CƠ SỞ DỮ LIỆU (REPOSITORIES)
    // =========================================================================
    private final IncidentTicketRepository incidentTicketRepository; // Thao tác bảng incident_tickets
    private final ParkingSessionRepository sessionRepository; // Thao tác bảng parking_sessions
    private final RfidCardRepository rfidCardRepository; // Thao tác bảng rfid_cards
    private final ZoneRepository zoneRepository; // Thao tác bảng zones
    private final com.pbms.modules.operation.repository.MonthlyTicketRepository monthlyTicketRepository; // Thao tác
                                                                                                         // bảng
                                                                                                         // monthly_tickets
    private final com.pbms.modules.identity.repository.UserRepository userRepository; // Thao tác bảng users
    private final com.pbms.modules.finance.repository.TransactionRepository transactionRepository; // Thao tác bảng
                                                                                                   // transactions
    private final com.pbms.modules.operation.repository.StaffWorkSessionRepository staffWorkSessionRepository; // Thao
                                                                                                               // tác
                                                                                                               // bảng
                                                                                                               // staff_work_sessions
    private final com.pbms.modules.operation.repository.VehicleRepository vehicleRepository; // Thao tác bảng vehicles

    // =========================================================================
    // NHÓM 2: CÁC DỊCH VỤ VÀ CÔNG CỤ PHỤ TRỢ (SERVICES & TOOLS)
    // =========================================================================
    private final SimpMessagingTemplate messagingTemplate; // Đẩy thông báo WebSocket tới kênh /topic/alerts
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService; // Tra cứu tiền phạt cấu hình
                                                                                           // hệ thống
    private final com.pbms.common.service.FileStorageService fileStorageService; // Lưu trữ ảnh Base64
    private final org.springframework.context.ApplicationContext applicationContext; // Spring Context lấy Bean ngầm khi
                                                                                     // cần

    // =========================================================================
    // PHẦN 5: CÁC HÀM XÁC THỰC VÀ BẢO VỆ AN NINH NGHIỆP VỤ (SECURITY HELPER
    // METHODS)
    // =========================================================================

    /**
     * =========================================================================
     * HÀM HỖ TRỢ: LẤY THÔNG TIN NGUỜI DÙNG ĐANG ĐĂNG NHẬP
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Đọc SecurityContext của Spring Security để xác định ai (Khách hàng/Bảo
     * vệ/Quản lý) đang thực hiện thao tác.
     * 
     * AI GỌI HÀM NÀY:
     * Được các hàm nghiệp vụ bên trong `IncidentService` gọi ngầm để ghi nhận thông
     * tin nhân viên xử lý (`setStaff`).
     * 
     * THAM SỐ ĐẦU VÀO & DỮ LIỆU TRẢ VỀ:
     * - Đầu vào: Không có (Đọc trực tiếp từ SecurityContextHolder).
     * - Trả về: Đối tượng User domain entity hoặc null nếu là người dùng vãng lai.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Lấy đối tượng Authentication từ
     * SecurityContextHolder.getContext().getAuthentication().
     * 2. Kiểm tra nếu auth không null, auth.getName() hợp lệ và khác
     * "anonymousUser":
     * -> Tìm kiếm người dùng trong userRepository theo Email = auth.getName().
     * 3. Trả về đối tượng User tìm được hoặc null.
     */
    private com.pbms.modules.identity.domain.User getCurrentUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    /**
     * =========================================================================
     * HÀM HỖ TRỢ: XÁC MINH QUYỀN SỞ HỮU PHƯƠNG TIỆN (VEHICLE OWNERSHIP
     * VERIFICATION)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Đảm bảo an ninh dữ liệu: Khách hàng thông thường chỉ được báo cáo sự cố cho
     * đúng xe do mình sở hữu.
     * Nhân viên (ROLE_STAFF) và Quản lý (ROLE_MANAGER) được miễn trừ kiểm tra để có
     * thể tạo sự cố giúp khách hàng.
     * 
     * AI GỌI HÀM NÀY:
     * Được gọi ở đầu các hàm `createIncident(...)` và
     * `createLostCardIncident(...)`.
     * 
     * THAM SỐ ĐẦU VÀO & DỮ LIỆU TRẢ VỀ:
     * - @param plate: Biển số xe cần kiểm tra.
     * - @param currentUserEmail: Email của tài khoản đang gửi request.
     * - Trả về: void. Ném lỗi IllegalArgumentException nếu phát hiện hành vi gian
     * lận báo cáo xe người khác.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Nếu biển số rỗng -> Cho qua (Return).
     * 2. Lấy danh sách Quyền (Authorities) từ SecurityContext. Nếu có ROLE_STAFF
     * hoặc ROLE_MANAGER -> Cho qua (Return).
     * 3. Chuẩn hóa biển số (Trim + Uppercase).
     * 4. Tìm kiếm vé tháng ACTIVE của biển số này. Nếu có -> Lấy email chủ vé
     * tháng.
     * 5. Nếu chưa có vé tháng -> Tìm kiếm xe trong bảng Vehicle. Nếu tìm thấy ->
     * Lấy email chủ xe.
     * 6. Nếu tìm thấy chủ xe và email chủ xe khác với `currentUserEmail`:
     * -> Ném ra lỗi: "Biển số xe này đã được đăng ký bởi một tài khoản khác...".
     */
    private void verifyVehicleOwnership(String plate, String currentUserEmail) {
        if (plate == null || plate.isBlank())
            return;

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null) {
            boolean isStaff = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_MANAGER"));
            if (isStaff)
                return;
        }

        String targetPlate = plate.trim().toUpperCase();
        String ownerEmail = null;

        java.util.Optional<com.pbms.modules.operation.domain.MonthlyTicket> mtOpt = monthlyTicketRepository
                .findByPlateNumberAndStatus(targetPlate, "ACTIVE");
        if (mtOpt.isPresent() && mtOpt.get().getUser() != null) {
            ownerEmail = mtOpt.get().getUser().getEmail();
        }

        if (ownerEmail == null) {
            java.util.Optional<com.pbms.modules.operation.domain.Vehicle> vehOpt = vehicleRepository
                    .findByPlateNumber(targetPlate);
            if (vehOpt.isPresent() && vehOpt.get().getUser() != null) {
                ownerEmail = vehOpt.get().getUser().getEmail();
            }
        }

        if (ownerEmail != null) {
            if (currentUserEmail == null || !ownerEmail.equalsIgnoreCase(currentUserEmail.trim())) {
                throw new IllegalArgumentException(
                        "Biển số xe này đã được đăng ký bởi một tài khoản khác. Chỉ chủ sở hữu xe hoặc Nhân viên quản lý mới có quyền báo cáo sự cố cho xe này!");
            }
        }
    }

    // =========================================================================
    // PHẦN 6: CÁC API NGHIỆP VỤ TẠO SỰ CỐ GIAI ĐOẠN 1 (CREATE INCIDENT & PHASE 1)
    // =========================================================================

    /**
     * =========================================================================
     * API / SERVICE: TẠO MỚI SỰ CỐ BÃI ĐỖ XE (CREATE INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Tiếp nhận yêu cầu báo cáo sự cố từ Khách hàng (qua App) hoặc Bảo vệ (qua
     * Console).
     * Tự động áp dụng mức tiền phạt cấu hình (PENALTY_LOST_CARD,
     * PENALTY_ZONE_VIOLATION...) và chuyển trạng thái ticket sang PENDING hoặc
     * WAITING_CHECKOUT.
     * 
     * AI GỌI HÀM NÀY:
     * Đổ về từ `POST /api/v1/incidents` do `IncidentTicketController` tiếp nhận.
     * 
     * THAM SỐ ĐẦU VÀO & DỮ LIỆU TRẢ VỀ:
     * - @param request: DTO chứa loại sự cố (LOST_CARD, LPR_MISMATCH,
     * ZONE_VIOLATION...), biển số, ảnh bằng chứng Base64.
     * - @param email: Email của người tạo sự cố.
     * - Trả về: Đối tượng IncidentTicket domain entity sau khi lưu DB và bắn
     * WebSocket.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm phiên đỗ xe (ParkingSession) tương ứng theo `sessionId` hoặc biển số +
     * loại xe đang đỗ (ACTIVE).
     * 2. Xác minh quyền sở hữu xe (`verifyVehicleOwnership`).
     * 3. Kiểm tra xem sự cố cùng loại có đang ở trạng thái PENDING/WAITING_CHECKOUT
     * hay không (Chống tạo trùng lặp).
     * 4. Lưu ảnh bằng chứng đính kèm (Base64) qua
     * `fileStorageService.storeBase64File(...)`.
     * 5. Khởi tạo đối tượng `IncidentTicket` với trạng thái "PENDING".
     * 6. Xử lý logic riêng biệt cho từng loại sự cố:
     * - LPR_MISMATCH: Cập nhật biển số xe đúng cho phiên đỗ và thẻ RFID.
     * - LOST_CARD: Đổi trạng thái thẻ RFID thành "LOST", lấy tiền phạt đền thẻ (mặc
     * định 200,000 VNĐ) gán vào phiên.
     * - DAMAGED_CARD: Đổi trạng thái thẻ RFID thành "DAMAGED".
     * - ZONE_VIOLATION: Áp tiền phạt đỗ sai khu vực (50k cho xe 2 bánh, 100k cho xe
     * 4 bánh), chuyển ticket sang "WAITING_CHECKOUT", bắn thông báo WebSocket.
     * - SLOT_OCCUPIED: Nâng ưu tiên HIGH cho xe vé tháng, chuyển khu vực gợi ý về
     * -1 (Khu vực tự do).
     * - BLACKLIST_VIOLATION: Phạt trốn phí, đóng phiên đỗ (COMPLETED) với tiền đỗ
     * xe = 0, đánh dấu thẻ "LOST".
     * 7. Gọi `saveAndBroadcast(ticket)` lưu CSDL và phát sóng WebSocket tới
     * `/topic/alerts`.
     */
    @Transactional
    public IncidentTicket createIncident(IncidentTicketRequest request, String email) {
        ParkingSession session = null;
        // 1. Nếu có session ID -> Tìm session qua ID
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));
            // 2. Nếu không có session ID -> Tìm session qua biển số xe và loại xe
        } else if (request.getPlate() != null && !request.getPlate().isBlank()) {
            if (request.getVehicleTypeId() == null) {
                throw new IllegalArgumentException("Loại phương tiện không được để trống.");
            }
            java.util.List<ParkingSession> activeSessions = sessionRepository.findByPlateAndVehicleTypeIdAndStatus(
                    request.getPlate().trim().toUpperCase(), request.getVehicleTypeId(), "ACTIVE");
            session = activeSessions.isEmpty() ? null : activeSessions.get(0);
        }
        // 3. Kiểm tra quyền sở hữu xe (chỉ áp dụng cho người dùng thông thường, nếu là
        // Staff hay Manager thì không áp dụng)
        String targetPlate = request.getPlate();
        if ((targetPlate == null || targetPlate.isBlank()) && session != null) {
            targetPlate = session.getPlate();
        }
        verifyVehicleOwnership(targetPlate, email);
        // Khi đã có session: Nếu request thiếu vehicleTypeId,
        // tự động móc id loại xe trong CSDL của session đắp ngược lại vào request.
        if (session != null) {
            if (request.getVehicleTypeId() == null) {
                if (session.getVehicleType() != null) {
                    request.setVehicleTypeId(session.getVehicleType().getId());
                } else {
                    throw new IllegalArgumentException("Loại phương tiện không được để trống.");
                }
            }
            // Kiểm tra xem vehicleTypeId trong request có trùng với vehicleTypeId trong
            // session không
            if (session.getVehicleType() != null
                    && !session.getVehicleType().getId().equals(request.getVehicleTypeId())) {
                throw new IllegalArgumentException(
                        "Biển số này thuộc về loại phương tiện khác trong hệ thống. Vui lòng kiểm tra lại loại xe.");
            }
        }
        // Ràng buộc lại một chiếc xe chỉ có thể báo bị chiếm chỗ một lần duy nhất,
        // tránh người dùng bấm nút báo làm ngập màn hình nhân viên.
        // Gọi incidentTicketRepository hàm existsBySessionIdAndIssueAndStatusIn để
        // check DB đã có sự cố bị chiếm chỗ chưa.

        if ("SLOT_OCCUPIED".equals(request.getIssueType())) {
            boolean hasSlotOccupied = false;
            if (session != null) {
                hasSlotOccupied = incidentTicketRepository.existsBySessionIdAndIssueTypeAndStatusIn(
                        session.getId(), "SLOT_OCCUPIED", java.util.Arrays.asList("PENDING", "WAITING_CHECKOUT"));
            } else if (request.getPlate() != null && !request.getPlate().isBlank()) {
            }
            if (hasSlotOccupied) {
                throw new IllegalArgumentException("Xe này đã báo cáo sự cố bị chiếm chỗ đang chờ xử lý!");
            }
        }

        if (session != null) {
            boolean exists = incidentTicketRepository.existsBySessionIdAndIssueTypeAndStatusIn(session.getId(),
                    request.getIssueType(), java.util.Arrays.asList("PENDING", "WAITING_CHECKOUT"));
            if (exists) {
                throw new IllegalArgumentException("Đã tồn tại một sự cố loại " + request.getIssueType()
                        + " đang chờ xử lý cho xe này trong phiên đỗ hiện tại!");
            }
        }

        com.pbms.modules.identity.domain.User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
        }

        IncidentTicket ticket = IncidentTicket.builder()
                .session(session)
                .user(user)
                .issueType(request.getIssueType())
                .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                .description(request.getDescription())
                .status("PENDING")
                .fineAmount(request.getFineAmount())
                .uploadedDocUrl(fileStorageService.storeBase64File(request.getUploadedDocUrl()))
                .build();

        if (session != null) {
            if ("LPR_MISMATCH".equals(request.getIssueType()) && request.getCorrectPlateNumber() != null) {
                session.setPlate(request.getCorrectPlateNumber());
                if (session.getRfidCard() != null) {
                    session.getRfidCard().setAssignedPlate(request.getCorrectPlateNumber());
                }
            } else if ("LOST_CARD".equals(request.getIssueType())) {
                if (session.getRfidCard() != null) {
                    session.getRfidCard().setStatus("LOST");
                    rfidCardRepository.save(session.getRfidCard());
                }

                BigDecimal fineToApply = request.getFineAmount();
                if (fineToApply == null) {
                    fineToApply = new BigDecimal("200000"); // default
                    try {
                        fineToApply = new BigDecimal(
                                systemConfigService.getConfigByKey("PENALTY_LOST_CARD").getConfigValue());
                    } catch (Exception e) {
                        log.warn("Could not find PENALTY_LOST_CARD config, using default");
                    }
                }
                session.setPenaltyFee(fineToApply);
                ticket.setFineAmount(fineToApply);
            } else if ("DAMAGED_CARD".equals(request.getIssueType())) {
                if (session.getRfidCard() != null) {
                    session.getRfidCard().setStatus("DAMAGED");
                    rfidCardRepository.save(session.getRfidCard());
                }
            }
        }

        if ("ZONE_VIOLATION".equals(request.getIssueType())) {
            BigDecimal fineToApply = request.getFineAmount();
            if (fineToApply == null) {
                boolean is2W = false;
                if (session != null && session.getVehicleType() != null
                        && "TWO_WHEEL".equals(session.getVehicleType().getCategory())) {
                    is2W = true;
                }
                String configKey = is2W ? "PENALTY_ZONE_VIOLATION_2W" : "PENALTY_ZONE_VIOLATION_4W";
                fineToApply = is2W ? new BigDecimal("50000") : new BigDecimal("100000");
                try {
                    fineToApply = new BigDecimal(systemConfigService.getConfigByKey(configKey).getConfigValue());
                } catch (Exception e) {
                    log.warn("Could not find {} config, using default", configKey);
                }
            }

            ticket.setFineAmount(fineToApply);

            if (session != null) {
                BigDecimal currentPenalty = session.getPenaltyFee() != null ? session.getPenaltyFee() : BigDecimal.ZERO;
                session.setPenaltyFee(currentPenalty.add(fineToApply));
                sessionRepository.save(session);
            }

            ticket.setStatus("WAITING_CHECKOUT");
            ticket.setResolutionNotes("[CONSOLE] Processed zone violation, penalty fee applied, waiting for checkout.");

            messagingTemplate.convertAndSend("/topic/alerts", "Zone violation warning: " + request.getDescription());
        }

        if ("SLOT_OCCUPIED".equals(request.getIssueType()) && session != null) {
            boolean isMonthly = monthlyTicketRepository.findByPlateNumberAndStatus(session.getPlate(), "ACTIVE")
                    .isPresent();
            if (isMonthly) {
                ticket.setPriority("HIGH");
                ticket.setDescription("[MONTHLY PASS CONFLICT] " + ticket.getDescription());
            } else {
                ticket.setPriority("MEDIUM");
            }
            session.setSuggestedZoneId(-1L);

            sessionRepository.save(session);
        }

        if ("BLACKLIST_VIOLATION".equals(request.getIssueType())) {
            BigDecimal fineToApply = request.getFineAmount();
            if (fineToApply == null) {
                boolean is2W = false;
                if (session != null && session.getVehicleType() != null
                        && "TWO_WHEEL".equals(session.getVehicleType().getCategory())) {
                    is2W = true;
                }
                String configKey = is2W ? "PENALTY_BLACKLIST_UNPAID_2W" : "PENALTY_BLACKLIST_UNPAID_4W";
                fineToApply = is2W ? new BigDecimal("50000") : new BigDecimal("100000"); // default
                try {
                    fineToApply = new BigDecimal(systemConfigService.getConfigByKey(configKey).getConfigValue());
                } catch (Exception e) {
                    log.warn("Could not find {} config, using default", configKey);
                }
            }

            ticket.setFineAmount(fineToApply);

            if (session != null) {
                BigDecimal currentPenalty = session.getPenaltyFee() != null ? session.getPenaltyFee() : BigDecimal.ZERO;
                session.setPenaltyFee(currentPenalty.add(fineToApply));
                session.setStatus("COMPLETED");
                session.setParkingFee(BigDecimal.ZERO);
                session.setTimeOut(com.pbms.common.utils.TimeProvider.now());
                if (session.getRfidCard() != null) {
                    com.pbms.modules.infrastructure.domain.RfidCard card = session.getRfidCard();
                    card.setStatus("LOST");
                    card.setAssignedPlate(null);
                    rfidCardRepository.save(card);
                }
                sessionRepository.save(session);
            }
        }

        return saveAndBroadcast(ticket);
    }

    // =========================================================================
    // PHẦN 7: TỰ ĐỘNG CRONJOB VÀ XỬ LÝ EVENT THỜI GIAN (OVERSTAY CRONJOB)
    // =========================================================================

    /**
     * =========================================================================
     * CRONJOB: TỰ ĐỘNG QUÉT XE ĐỖ QUÁ HẠN (OVERSTAY CHECKING CRONJOB)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Chạy định kỳ lúc 2:00 AM hằng ngày để tự động quét toàn bộ bãi đỗ.
     * Phát hiện các xe đỗ liên tục quá số giờ giới hạn (mặc định 72h) và tự động
     * sinh sự cố OVERSTAY cảnh báo nhân viên.
     * 
     * AI GỌI HÀM NÀY:
     * Spring Scheduler tự động gọi theo lịch `@Scheduled(cron = "0 0 2 * * ?")`
     * hoặc kích hoạt ngầm từ `handleTimeFastForward(...)`.
     * 
     * THAM SỐ ĐẦU VÀO & DỮ LIỆU TRẢ VỀ:
     * - Đầu vào: Không có.
     * - Trả về: void.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Đọc cấu hình `OVERSTAY_HOURS_LIMIT` từ CSDL (mặc định 72 giờ).
     * 2. Tính thời điểm mốc (cutoff = Thời gian hiện tại - hoursLimit).
     * 3. Tìm các phiên đỗ đang ACTIVE có thời điểm vào `timeIn` trước mốc cutoff.
     * 4. Lặp qua từng phiên đỗ:
     * -> Nếu phiên này chưa có sự cố OVERSTAY -> Tạo mới sự cố OVERSTAY với ưu tiên
     * HIGH.
     * -> Gọi `saveAndBroadcast(...)` và phát tin nhắn WebSocket tới
     * `/topic/alerts`.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void handleOverstayVehicles() {
        log.info("Starting OVERSTAY checking cronjob...");
        int hoursLimit = 72;
        try {
            hoursLimit = Integer.parseInt(systemConfigService.getConfigByKey("OVERSTAY_HOURS_LIMIT").getConfigValue());
        } catch (Exception e) {
            log.warn("OVERSTAY_HOURS_LIMIT config not found, using default 72");
        }

        LocalDateTime cutoff = com.pbms.common.utils.TimeProvider.now().minusHours(hoursLimit);
        List<ParkingSession> overstaySessions = sessionRepository.findActiveSessionsOlderThan(cutoff);

        for (ParkingSession session : overstaySessions) {
            boolean hasAnyOverstay = incidentTicketRepository.findBySessionId(session.getId()).stream()
                    .anyMatch(t -> "OVERSTAY".equals(t.getIssueType()));

            if (!hasAnyOverstay) {
                IncidentTicket ticket = IncidentTicket.builder()
                        .session(session)
                        .issueType("OVERSTAY")
                        .priority("HIGH")
                        .description(
                                String.format("Hệ thống tự động ghi nhận xe đỗ quá hạn (Không áp phí phạt) (%s: %s)",
                                        session.getPlate(), session.getTimeIn().toString()))
                        .status("PENDING")
                        .build();
                saveAndBroadcast(ticket);
                log.warn("Created OVERSTAY incident for plate: {}", session.getPlate());
                messagingTemplate.convertAndSend("/topic/alerts",
                        "OVERSTAY incident generated for plate: " + session.getPlate());
            }
        }
    }

    /**
     * =========================================================================
     * EVENT LISTENER: LẮNG NGHE SỰ KIỆN TUA THỜI GIAN (TIME FAST FORWARD)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Khi Admin tua nhanh thời gian trên Simulator, kiểm tra xem khoảng thời gian
     * tua có nhảy qua mốc 2:00 AM hay không.
     * Nếu có -> Kích hoạt ngay hàm `handleOverstayVehicles()` để dọn dẹp xe quá hạn
     * mà không cần chờ đến 2h sáng thực tế.
     */
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime oldTime = event.getOldSimulatedTime();
        LocalDateTime newTime = event.getNewSimulatedTime();
        if (hasCrossedTime(oldTime, newTime, 2, 0)) {
            handleOverstayVehicles();
        }
    }

    /**
     * Kiểm tra khoảng thời gian từ oldTime đến newTime có đi qua mốc giờ
     * targetHour:targetMinute hay không.
     */
    private boolean hasCrossedTime(LocalDateTime oldTime, LocalDateTime newTime, int targetHour, int targetMinute) {
        if (oldTime == null || newTime == null || !oldTime.isBefore(newTime))
            return false;

        LocalDateTime targetInOldDay = oldTime.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (oldTime.isBefore(targetInOldDay) && !newTime.isBefore(targetInOldDay)) {
            return true;
        }

        LocalDateTime targetInNewDay = newTime.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (oldTime.isBefore(targetInNewDay) && !newTime.isBefore(targetInNewDay)) {
            return true;
        }

        if (java.time.Duration.between(oldTime, newTime).toHours() >= 24) {
            return true;
        }
        return false;
    }

    // =========================================================================
    // PHẦN 8: TRUY VẤN VÀ DUYỆT SỰ CỐ GIAI ĐOẠN 1 (PROCESS PHASE 1 & OVERSTAY)
    // =========================================================================

    /**
     * LẤY DANH SÁCH SỰ CỐ (GET ALL INCIDENTS)
     * - Nếu truyền email khách hàng: Lấy danh sách sự cố của chính khách đó.
     * - Nếu không truyền email (Nhân viên/Quản lý): Lấy toàn bộ danh sách sự cố
     * giảm dần theo ID.
     */
    @Transactional(readOnly = true)
    public List<IncidentTicketDTO> getAllIncidents(String email) {
        List<IncidentTicket> tickets;
        if (email != null && !email.isBlank()) {
            tickets = incidentTicketRepository.findAllByUserEmailOrVehicleOwner(email);
        } else {
            tickets = incidentTicketRepository.findAllByOrderByIdDesc();
        }
        return tickets.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Nhân viên xác nhận đã xem sự cố đỗ quá hạn (OVERSTAY) -> Chuyển trạng thái
     * sang WAITING_CHECKOUT.
     */
    @Transactional
    public IncidentTicketDTO acknowledgeOverstay(Long id) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!"PENDING".equals(ticket.getStatus()) && !"OVERSTAY".equals(ticket.getIssueType())) {
            throw new IllegalStateException("Ticket must be PENDING and of type OVERSTAY");
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes("Xác nhận đã xem bởi nhân viên");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setStatus("WAITING_CHECKOUT");
        ticket.setStaff(getCurrentUser());
        return mapToDTO(saveAndBroadcast(ticket));
    }

    /**
     * Di chuyển xe đỗ quá hạn sang khu vực Overstay và đóng sự cố.
     */
    @Transactional
    public IncidentTicketDTO moveToOverstay(Long id, String uploadedDocUrl) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!"PENDING".equals(ticket.getStatus()) && !"OVERSTAY".equals(ticket.getIssueType())) {
            throw new IllegalStateException("Ticket must be PENDING and of type OVERSTAY");
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes("[OVERSTAY] Vehicle moved to overstay zone");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setStaff(getCurrentUser());
        if (uploadedDocUrl != null && !uploadedDocUrl.isBlank()) {
            ticket.setUploadedDocUrl(fileStorageService.storeBase64File(uploadedDocUrl));
        }

        return mapToDTO(saveAndBroadcast(ticket));
    }

    /**
     * =========================================================================
     * API / SERVICE: DUYỆT SỰ CỐ GIAI ĐOẠN 1 (PROCESS PHASE 1)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Nhân viên kiểm tra hồ sơ sự cố ban đầu (ảnh bằng chứng, lý do mất thẻ/hỏng
     * thẻ),
     * thiết lập số tiền phạt/giảm giá nếu có, và duyệt chuyển sự cố sang trạng thái
     * WAITING_CHECKOUT (Giai đoạn 2).
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm ticket theo ID. Kiểm tra trạng thái phải là PENDING.
     * 2. Nếu sự cố là loại "OTHER" hoặc "OTHER_FEEDBACK": Ràng buộc chỉ Quản lý
     * (ROLE_MANAGER/SUPER_ADMIN) mới có quyền duyệt.
     * 3. Gán nhân viên xử lý `setStaff(getCurrentUser())`.
     * 4. Đổi trạng thái ticket sang WAITING_CHECKOUT.
     * 5. Cập nhật số tiền phạt `fineAmount` mới vào phiên đỗ xe (ParkingSession).
     * 6. Nếu là sự cố xe danh sách đen `BLACKLIST_VIOLATION`: Khóa thẻ RFID thành
     * "LOST", cập nhật thông tin xe thành Blacklist.
     * 7. Lưu ảnh bằng chứng Phase 1, gửi thông báo WebSocket tới `/topic/alerts`.
     */
    @Transactional
    public IncidentTicketDTO processPhase1(Long id, String resolutionNotes, String resolutionImageUrl,
            java.math.BigDecimal fineAmount, java.math.BigDecimal discountAmount) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if (!"PENDING".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is already resolved or in an invalid state");
        }

        if ("OTHER".equals(ticket.getIssueType()) || "OTHER_FEEDBACK".equals(ticket.getIssueType())) {
            if (!"MANAGER".equals(getCurrentUser().getRole()) && !"SUPER_ADMIN".equals(getCurrentUser().getRole())) {
                throw new IllegalStateException("Chỉ Quản lý mới có quyền duyệt sự cố hình phạt khác hoặc góp ý.");
            }
        }

        ticket.setStaff(getCurrentUser());

        ParkingSession ticketSession = ticket.getSession();
        if ("OTHER_FEEDBACK".equals(ticket.getIssueType()) || ticketSession == null
                || "COMPLETED".equals(ticketSession.getStatus())) {
            ticket.setStatus("RESOLVED");
            ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        } else {
            ticket.setStatus("WAITING_CHECKOUT");
        }

        if (fineAmount != null) {
            java.math.BigDecimal oldFine = ticket.getFineAmount() != null ? ticket.getFineAmount()
                    : java.math.BigDecimal.ZERO;
            ticket.setFineAmount(fineAmount);
            ParkingSession session = ticket.getSession();
            if (session != null) {
                java.math.BigDecimal currentPenalty = session.getPenaltyFee() != null ? session.getPenaltyFee()
                        : java.math.BigDecimal.ZERO;
                session.setPenaltyFee(currentPenalty.subtract(oldFine).add(fineAmount));
                sessionRepository.save(session);
            }
        }

        if ("BLACKLIST_VIOLATION".equals(ticket.getIssueType())) {
            ticket.setStatus("WAITING_CHECKOUT");
            if (ticketSession != null && "ACTIVE".equals(ticketSession.getStatus())) {
                ticketSession.setStatus("COMPLETED");
                ticketSession.setTimeOut(com.pbms.common.utils.TimeProvider.now());
                ticketSession.setParkingFee(java.math.BigDecimal.ZERO);
                if (ticketSession.getRfidCard() != null) {
                    com.pbms.modules.infrastructure.domain.RfidCard card = ticketSession.getRfidCard();
                    card.setStatus("LOST");
                    card.setAssignedPlate(null);
                    rfidCardRepository.save(card);
                }
                sessionRepository.save(ticketSession);
                log.info("Blacklisted vehicle session {} closed with 0 fee and card LOST.", ticketSession.getId());
            }
            String plateToBlacklist = ticketSession != null ? ticketSession.getPlate() : null;
            if (plateToBlacklist != null) {
                com.pbms.modules.operation.domain.Vehicle v = vehicleRepository.findByPlateNumber(plateToBlacklist)
                        .orElseGet(() -> {
                            com.pbms.modules.operation.domain.Vehicle newV = new com.pbms.modules.operation.domain.Vehicle();
                            newV.setPlateNumber(plateToBlacklist);
                            newV.setStatus("ACTIVE");
                            if (ticketSession != null && ticketSession.getVehicleType() != null) {
                                newV.setVehicleType(ticketSession.getVehicleType());
                            }
                            return newV;
                        });
                v.setIsBlacklisted(true);
                vehicleRepository.save(v);
                log.info("Vehicle {} blacklisted in Phase 1.", v.getPlateNumber());
            }
        }

        if ("FEE_DISPUTE".equals(ticket.getIssueType()) && discountAmount != null) {
            ParkingSession session = ticket.getSession();
            if (session != null) {
                session.setDiscount(discountAmount);
                sessionRepository.save(session);
            }
        }

        String notes = resolutionNotes != null && !resolutionNotes.isBlank() ? resolutionNotes
                : "Đã xác minh thông tin.";
        ticket.setResolutionNotes("[Phase 1] " + notes);

        if (resolutionImageUrl != null && !resolutionImageUrl.isBlank()) {
            ticket.setResolutionImageUrl("[P1]" + fileStorageService.storeBase64File(resolutionImageUrl));
        }

        boolean isCardIncident = "LOST_CARD".equals(ticket.getIssueType())
                || "DAMAGED_CARD".equals(ticket.getIssueType());

        if (isCardIncident) {
            messagingTemplate.convertAndSend("/topic/alerts",
                    "[GD1 OK] Phien #" + id + " da xac nhan mat/hong the. Cho thu tien de xu ly.");
        } else {
            messagingTemplate.convertAndSend("/topic/alerts",
                    "[GD1 OK] Ticket #" + id + " dang duoc nhan vien xu ly ho tro.");
        }

        log.info("Incident #{} moved to WAITING_CHECKOUT (Phase 1 approved)", id);
        return mapToDTO(saveAndBroadcast(ticket));
    }

    // =========================================================================
    // PHẦN 9: GIAI ĐOẠN 2 - THANH TOÁN VÀ HOÀN TẤT SỰ CỐ (RESOLVE PHASE 2)
    // =========================================================================

    /**
     * =========================================================================
     * API / SERVICE: HOÀN TẤT VÀ THANH TOÁN SỰ CỐ GIAI ĐOẠN 2 (RESOLVE PHASE 2)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Thu tiền phạt + tiền đỗ xe thực tế tại quầy, thực hiện kiểm tra an ninh 2 lớp
     * bằng JWT Checkout Token,
     * đóng phiên đỗ xe (COMPLETED), ghi nhận giao dịch tài chính (Transaction),
     * khôi phục/khóa thẻ RFID và chuyển ticket sang RESOLVED.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm sự cố theo ID. Đổi trạng thái sang "RESOLVED".
     * 2. Kiềm tra an ninh 2 lớp qua JWT Checkout Token (`2-Layer Validation`):
     * -> Giải mã `checkoutToken` qua `JwtProvider`.
     * -> Đối soát `sessionId` và số tiền gốc `expectedFee` trong Token với tổng số
     * tiền truyền lên (`parkingFee + penaltyFee - discountAmount`).
     * -> Nếu sai lệch quá 1 VNĐ hoặc Token quá hạn 5 phút -> Ném lỗi từ chối thu
     * tiền.
     * 3. Kiểm tra mức tiền phạt cố định theo quy định (Ví dụ: Mất thẻ, đền thẻ
     * hỏng, vi phạm khu vực).
     * 4. Hoàn tất phiên đỗ xe: Đặt status = COMPLETED, gán thời gian ra `timeOut`,
     * ảnh xe ra `picOutPanorama`, cổng ra `gateOut`.
     * 5. Ghi nhận giao dịch tài chính: Nếu tổng số tiền > 0 -> Tạo đối tượng
     * `Transaction` lưu vào `transactionRepository`.
     * 6. Cập nhật thẻ RFID: Mất thẻ -> "LOST", Hỏng thẻ -> "DAMAGED", Bình thường
     * -> "AVAILABLE" (Gỡ bỏ biển số gán trên thẻ).
     * 7. Nếu là sự cố xe danh sách đen `BLACKLIST_VIOLATION`: Xóa cờ Blacklist khỏi
     * bảng Vehicle.
     * 8. Phát sóng thông báo WebSocket qua `saveAndBroadcast(ticket)`.
     */
    @Transactional
    public IncidentTicketDTO resolveIncident(Long id, String resolutionNotes, String resolutionImageUrl,
            String uploadedPicOutUrl, java.math.BigDecimal parkingFee, java.math.BigDecimal penaltyFee,
            java.math.BigDecimal discountAmount, String paymentMethod, Long paymentOrderId, String checkoutToken) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!"PENDING".equals(ticket.getStatus()) && !"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is already resolved or in a different status");
        }

        ParkingSession session = ticket.getSession();

        ticket.setStatus("RESOLVED");

        String newNotes = resolutionNotes != null ? resolutionNotes : "[RESOLVED] Đã xử lý hoàn tất.";
        String methodStr = paymentMethod != null ? paymentMethod : "CASH";
        String methodDisplay = methodStr.equals("CASH") ? "Tiền mặt" : methodStr;

        if (penaltyFee != null || parkingFee != null) {
            newNotes += "\nHình thức thanh toán: " + methodDisplay;
        }

        if (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank()) {
            ticket.setResolutionNotes(ticket.getResolutionNotes() + "\n[Phase 2] " + newNotes);
        } else {
            ticket.setResolutionNotes("[Phase 2] " + newNotes);
        }

        if (resolutionImageUrl != null && !resolutionImageUrl.isBlank()) {
            String storedImgUrl = fileStorageService.storeBase64File(resolutionImageUrl);
            if (ticket.getResolutionImageUrl() != null && !ticket.getResolutionImageUrl().isBlank()) {
                ticket.setResolutionImageUrl(ticket.getResolutionImageUrl() + "|[P2]" + storedImgUrl);
            } else {
                ticket.setResolutionImageUrl("[P2]" + storedImgUrl);
            }
        }

        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setStaff(getCurrentUser());

        if (penaltyFee != null) {
            ticket.setFineAmount(penaltyFee);
        }

        // 2-Layer Validation Check with JWT Token
        if (session != null && ("ACTIVE".equals(session.getStatus()) || "LOCKED".equals(session.getStatus()))) {
            com.pbms.common.security.JwtProvider jwtProvider = applicationContext
                    .getBean(com.pbms.common.security.JwtProvider.class);

            if (checkoutToken == null || checkoutToken.isEmpty()) {
                throw new IllegalArgumentException(
                        "Vui lòng tải lại (refresh) thông tin sự cố để lấy mã xác thực phí mới nhất trước khi xác nhận thu tiền.");
            }

            try {
                io.jsonwebtoken.Claims claims = jwtProvider.getCheckoutClaims(checkoutToken);
                if (!String.valueOf(session.getId()).equals(claims.get("sessionId", String.class))) {
                    throw new IllegalArgumentException("Mã xác thực không khớp với phiên đỗ xe hiện tại.");
                }

                double tokenExpectedFee = claims.get("expectedFee", Double.class);
                java.math.BigDecimal providedParkingFee = parkingFee != null ? parkingFee : java.math.BigDecimal.ZERO;
                java.math.BigDecimal providedPenaltyFee = penaltyFee != null ? penaltyFee : java.math.BigDecimal.ZERO;
                java.math.BigDecimal providedDiscount = discountAmount != null ? discountAmount
                        : java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalProvidedFee = providedParkingFee.add(providedPenaltyFee)
                        .subtract(providedDiscount);

                if (java.math.BigDecimal.valueOf(tokenExpectedFee).subtract(totalProvidedFee).abs()
                        .compareTo(java.math.BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException("Phí thanh toán có sự sai lệch với tính toán gốc (Thực tế gốc: "
                            + String.format("%,.0f", tokenExpectedFee)
                            + " VNĐ). Nhấn Cập nhật lại phí và thực hiện thanh toán lại.");
                }

            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                throw new IllegalArgumentException(
                        "Báo giá đã hết hạn (quá 5 phút). Vui lòng làm mới trang (refresh) để xem báo giá mới nhất.");
            } catch (Exception e) {
                throw new IllegalArgumentException("Token báo giá không hợp lệ: " + e.getMessage());
            }
        }

        java.math.BigDecimal expectedPenaltyFee = ticket.getFineAmount() != null ? ticket.getFineAmount()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal providedPenaltyFee = penaltyFee != null ? penaltyFee : java.math.BigDecimal.ZERO;

        if ("LOST_CARD".equals(ticket.getIssueType()) || "ZONE_VIOLATION".equals(ticket.getIssueType())
                || "BLACKLIST_VIOLATION".equals(ticket.getIssueType())) {
            if (expectedPenaltyFee.compareTo(providedPenaltyFee) != 0) {
                throw new IllegalArgumentException("Phí phạt không khớp với hệ thống. Bắt buộc: "
                        + String.format("%,d", expectedPenaltyFee.longValue()) + " VNĐ");
            }
        } else if ("DAMAGED_CARD".equals(ticket.getIssueType())) {
            java.math.BigDecimal systemDamagedFee = new java.math.BigDecimal("50000");
            try {
                systemDamagedFee = new java.math.BigDecimal(
                        systemConfigService.getConfigByKey("PENALTY_DAMAGED_CARD").getConfigValue());
            } catch (Exception e) {
            }

            if (providedPenaltyFee.compareTo(java.math.BigDecimal.ZERO) != 0
                    && providedPenaltyFee.compareTo(systemDamagedFee) != 0) {
                throw new IllegalArgumentException("Phí phạt đền thẻ không hợp lệ. Chỉ được phép 0 VNĐ (Hao mòn) hoặc "
                        + String.format("%,d", systemDamagedFee.longValue()) + " VNĐ (Lỗi người dùng)");
            }
        }
        if (session != null && ("ACTIVE".equals(session.getStatus()) || "LOCKED".equals(session.getStatus()))) {
            session.setStatus("COMPLETED");
            if (session.getTimeOut() == null) {
                session.setTimeOut(com.pbms.common.utils.TimeProvider.now());
            }
            if (uploadedPicOutUrl != null && !uploadedPicOutUrl.isBlank()) {
                session.setPicOutPanorama(uploadedPicOutUrl);
            }

            java.math.BigDecimal netParkingFee = java.math.BigDecimal.ZERO;
            if (parkingFee != null) {
                netParkingFee = parkingFee;
                if (discountAmount != null) {
                    netParkingFee = netParkingFee.subtract(discountAmount);
                    if (netParkingFee.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        netParkingFee = java.math.BigDecimal.ZERO;
                    }
                }
                session.setParkingFee(netParkingFee);
            }
            if (penaltyFee != null) {
                session.setPenaltyFee(penaltyFee);
            }

            com.pbms.modules.identity.domain.User staff = getCurrentUser();
            if (staff != null) {
                com.pbms.modules.identity.domain.StaffWorkSession workSession = staffWorkSessionRepository
                        .findByStaffIdAndStatus(staff.getId(), "ACTIVE").orElse(null);
                if (workSession != null && workSession.getGate() != null) {
                    session.setGateOut(workSession.getGate());
                }
            }

            java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;
            if (netParkingFee != null)
                totalAmount = totalAmount.add(netParkingFee);
            if (penaltyFee != null)
                totalAmount = totalAmount.add(penaltyFee);

            if (totalAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                com.pbms.modules.identity.domain.StaffWorkSession currentSession = null;
                if (staff != null) {
                    currentSession = staffWorkSessionRepository.findByStaffIdAndStatus(staff.getId(), "ACTIVE")
                            .orElse(null);
                }

                com.pbms.modules.finance.domain.PaymentOrder po = null;
                if (paymentOrderId != null) {
                    po = new com.pbms.modules.finance.domain.PaymentOrder();
                    po.setId(paymentOrderId);
                }

                com.pbms.modules.finance.domain.Transaction transaction = com.pbms.modules.finance.domain.Transaction
                        .builder()
                        .parkingSession(session)
                        .workSession(currentSession)
                        .paymentOrder(po)
                        .amount(totalAmount)
                        .paymentMethod(paymentMethod != null ? paymentMethod : "CASH")
                        .status("SUCCESS")
                        .transactionReference("TXN-" + session.getId() + "-"
                                + com.pbms.common.utils.TimeProvider.now().toInstant(java.time.ZoneOffset.UTC)
                                        .toEpochMilli())
                        .build();
                transactionRepository.save(transaction);
                log.info("Transaction recorded from Incident Desk: {} {} via {}", totalAmount, "VND",
                        transaction.getPaymentMethod());
            }

            if (session.getRfidCard() != null) {
                RfidCard card = session.getRfidCard();
                if ("LOST_CARD".equals(ticket.getIssueType())) {
                    card.setStatus("LOST");
                } else if ("DAMAGED_CARD".equals(ticket.getIssueType())) {
                    card.setStatus("DAMAGED");
                } else {
                    card.setStatus("AVAILABLE");
                }
                card.setAssignedPlate(null);
                rfidCardRepository.save(card);
            }

            sessionRepository.save(session);
        }

        if ("BLACKLIST_VIOLATION".equals(ticket.getIssueType())) {
            String plateToUnblacklist = session != null ? session.getPlate() : null;
            if (plateToUnblacklist != null) {
                vehicleRepository.findByPlateNumber(plateToUnblacklist).ifPresent(v -> {
                    v.setIsBlacklisted(false);
                    vehicleRepository.save(v);
                    log.info("Vehicle {} unblacklisted because BLACKLIST_VIOLATION incident was resolved/paid",
                            v.getPlateNumber());
                });
            }
        }

        return mapToDTO(saveAndBroadcast(ticket));
    }

    /**
     * =========================================================================
     * HỦY SỰ CỐ (CANCEL INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Khôi phục trạng thái ban đầu của phiên đỗ (ACTIVE) và thẻ RFID (IN_USE) nếu
     * sự cố bị hủy.
     * Sử dụng khi khách hàng hoặc nhân viên báo cáo sai và muốn rút lại báo cáo.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.cancelIncident`: Gọi từ màn hình "Hủy sự cố" của
     * nhân viên hoặc khách.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm sự cố bằng ID. Nếu không có, ném lỗi IllegalArgumentException.
     * 2. Nếu là lỗi BLACKLIST_VIOLATION, kiểm tra xe còn trong bãi hay không. Nếu
     * có xe trong bãi thì cấm hủy.
     * 3. Chuyển trạng thái sự cố sang CANCELLED, cập nhật lý do và phân loại hủy.
     * 4. Thêm ảnh hủy (nếu có) vào danh sách ảnh bằng thẻ [CX].
     * 5. Gán người xử lý là nhân viên hiện tại, đặt tiền phạt về 0.
     * 6. Khôi phục phiên đỗ:
     * - Nếu phiên bị LOCKED, trả lại ACTIVE, gỡ TimeOut và ParkingFee.
     * - Nếu là mất/hỏng thẻ, lấy thẻ RFID trả lại trạng thái IN_USE.
     * - Nếu là tranh chấp phí, xóa chiết khấu (Discount).
     * - Khấu trừ tiền phạt sự cố khỏi tổng tiền phạt của phiên.
     * 7. Nếu là BLACKLIST_VIOLATION, gỡ biển số khỏi Blacklist.
     * 8. Lưu DB và phát sóng WebSocket.
     */
    @Transactional
    public IncidentTicketDTO cancelIncident(Long id, String reason, String cancelType, String cancelImageUrl) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if ("BLACKLIST_VIOLATION".equals(ticket.getIssueType())) {
            String plateToCheck = ticket.getSession() != null ? ticket.getSession().getPlate() : null;
            if (plateToCheck != null) {
                boolean hasActive = sessionRepository.findByPlateOrderByTimeInDesc(plateToCheck).stream()
                        .anyMatch(s -> "ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus()));
                if (hasActive) {
                    throw new IllegalStateException(
                            "Không thể hủy sự cố Blacklist khi xe đang ở trong bãi (đã vào bãi). Vui lòng xử lý thanh toán tại cổng ra!");
                }
            }
        }

        ticket.setStatus("CANCELLED");
        ticket.setCancelType(cancelType);

        String cancelNotes = "[CANCELLED] " + (reason != null ? reason : "Sự cố đã bị hủy.");
        if (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank()) {
            ticket.setResolutionNotes(ticket.getResolutionNotes() + "\n" + cancelNotes);
        } else {
            ticket.setResolutionNotes(cancelNotes);
        }
        if (cancelImageUrl != null && !cancelImageUrl.isBlank()) {
            String storedImgUrl = fileStorageService.storeBase64File(cancelImageUrl);
            if (ticket.getResolutionImageUrl() != null && !ticket.getResolutionImageUrl().isBlank()) {
                ticket.setResolutionImageUrl(ticket.getResolutionImageUrl() + "|[CX]" + storedImgUrl);
            } else {
                ticket.setResolutionImageUrl("[CX]" + storedImgUrl);
            }
        }

        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setStaff(getCurrentUser());
        ticket.setFineAmount(java.math.BigDecimal.ZERO);

        ParkingSession session = ticket.getSession();
        if (session != null) {
            boolean isBlacklistViolation = "BLACKLIST_VIOLATION".equals(ticket.getIssueType());
            boolean isCardIncident = "LOST_CARD".equals(ticket.getIssueType())
                    || "DAMAGED_CARD".equals(ticket.getIssueType());
            if ("ACTIVE".equals(session.getStatus()) || "LOCKED".equals(session.getStatus())
                    || (isBlacklistViolation && "COMPLETED".equals(session.getStatus()))) {
                if ("LOCKED".equals(session.getStatus())
                        || (isBlacklistViolation && "COMPLETED".equals(session.getStatus()))) {
                    session.setStatus("ACTIVE");
                    session.setTimeOut(null);
                    session.setParkingFee(null);
                    if ((isBlacklistViolation || isCardIncident) && session.getRfidCard() != null) {
                        com.pbms.modules.infrastructure.domain.RfidCard card = session.getRfidCard();
                        card.setStatus("IN_USE");
                        card.setAssignedPlate(session.getPlate());
                        rfidCardRepository.save(card);
                    }
                }
                if ("FEE_DISPUTE".equals(ticket.getIssueType())) {
                    session.setDiscount(null);
                }
                if (ticket.getFineAmount() != null && ticket.getFineAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    java.math.BigDecimal currentPenalty = session.getPenaltyFee() != null ? session.getPenaltyFee()
                            : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal newPenalty = currentPenalty.subtract(ticket.getFineAmount());
                    if (newPenalty.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        newPenalty = java.math.BigDecimal.ZERO;
                    }
                    session.setPenaltyFee(newPenalty);
                }
                sessionRepository.save(session);
                log.info("ParkingSession #{}" + " restored to ACTIVE (incident cancelled)", session.getId());

                if (isBlacklistViolation) {
                    vehicleRepository.findByPlateNumber(session.getPlate()).ifPresent(v -> {
                        v.setIsBlacklisted(false);
                        vehicleRepository.save(v);
                        log.info("Vehicle {} unblacklisted because BLACKLIST_VIOLATION incident was cancelled",
                                session.getPlate());
                    });
                }
            }
        }

        log.info("Incident #{}" + " CANCELLED. Reason: {}", id, reason);
        return mapToDTO(saveAndBroadcast(ticket));
    }

    /**
     * =========================================================================
     * GIẢI QUYẾT SỰ CỐ KHÔNG LIÊN QUAN ĐẾN THẺ (RESOLVE NON-CARD INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Hoàn tất các sự cố không phải hỏng thẻ/mất thẻ (vd: đỗ sai bãi, xe trong danh
     * sách đen...).
     * 
     * AI GỌI HÀM NÀY:
     * - Không trực tiếp sử dụng (chủ yếu được gọi hoặc có logic tương tự ở
     * `resolveIncident`).
     * - Hoặc dùng làm hàm tiện ích nội bộ cho các trường hợp đặc biệt.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm sự cố bằng ID.
     * 2. Kiểm tra nếu trạng thái không phải WAITING_CHECKOUT thì từ chối.
     * 3. Đánh dấu trạng thái thành RESOLVED.
     * 4. Ghi nhận thời gian hoàn tất, nhân viên thực hiện.
     * 5. Lưu ảnh xác nhận và ghi chú giải quyết.
     * 6. Lưu DB, phát sóng qua WebSocket cho các client và trả về DTO.
     */
    @Transactional
    public IncidentTicketDTO resolveNonCardIncident(Long id, String resolutionNotes, String docUrl) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if (!"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Incident ticket is not in a valid state to be resolved.");
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setStaff(getCurrentUser());
        if (docUrl != null)
            ticket.setResolutionImageUrl(fileStorageService.storeBase64File(docUrl));
        ticket.setResolutionNotes(resolutionNotes != null ? resolutionNotes : "Incident resolved successfully.");

        log.info("Incident #{}" + " RESOLVED (Non-card flow)", id);
        messagingTemplate.convertAndSend("/topic/alerts",
                "[RESOLVED] Ticket #" + id + " has been successfully resolved.");

        return mapToDTO(saveAndBroadcast(ticket));
    }

    /**
     * =========================================================================
     * TỪ CHỐI XỬ LÝ SỰ CỐ (REJECT INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Từ chối ticket nếu thông tin khai báo sai hoặc giấy tờ không hợp lệ.
     * Khác với Cancel (do người dùng tự hủy), Reject là do Ban quản lý/Staff bác bỏ
     * yêu cầu của khách.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.rejectIncident`: Gọi từ nút "Từ chối" trên màn
     * hình quản lý.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm sự cố bằng ID.
     * 2. Nếu đã RESOLVED/REJECTED thì không cho từ chối tiếp.
     * 3. Ràng buộc quyền: STAFF không được từ chối nếu sự cố đã sang Giai đoạn 2
     * (WAITING_CHECKOUT), chỉ Manager mới được.
     * 4. Trừ đi tiền phạt của sự cố khỏi tổng tiền phạt của phiên đỗ (PenaltyFee)
     * vì sự cố bị bác bỏ.
     * 5. Nếu là sự cố thẻ, trả trạng thái thẻ về IN_USE.
     * 6. Cập nhật trạng thái sự cố sang REJECTED cùng ghi chú từ chối.
     * 7. Lưu DB và phát sóng WebSocket.
     */
    @Transactional
    public IncidentTicketDTO rejectIncident(Long id, String reason) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if ("RESOLVED".equals(ticket.getStatus()) || "REJECTED".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket da o trang thai cuoi, khong the tu choi");
        }

        if ("WAITING_CHECKOUT".equals(ticket.getStatus()) && "STAFF".equals(getCurrentUser().getRole())) {
            throw new IllegalStateException("Sự cố đã qua Phase 1. Chỉ Quản lý mới có quyền Hủy ở giai đoạn này.");
        }

        if (ticket.getFineAmount() != null && ticket.getFineAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            ParkingSession session = ticket.getSession();
            if (session != null && session.getPenaltyFee() != null) {
                java.math.BigDecimal newPenalty = session.getPenaltyFee().subtract(ticket.getFineAmount());
                if (newPenalty.compareTo(java.math.BigDecimal.ZERO) < 0) {
                    newPenalty = java.math.BigDecimal.ZERO;
                }
                session.setPenaltyFee(newPenalty);
                sessionRepository.save(session);
            }
        }

        if (("LOST_CARD".equals(ticket.getIssueType()) || "DAMAGED_CARD".equals(ticket.getIssueType()))
                && ticket.getSession() != null) {
            com.pbms.modules.infrastructure.domain.RfidCard card = ticket.getSession().getRfidCard();
            if (card != null) {
                card.setStatus("IN_USE");
                rfidCardRepository.save(card);
            }
        }

        ticket.setStatus("REJECTED");
        ticket.setResolutionNotes("Từ chối xử lý: " + reason);
        ticket.setStaff(getCurrentUser());
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());

        log.info("Incident #{}" + " REJECTED. Reason: {}", id, reason);
        return mapToDTO(saveAndBroadcast(ticket));
    }

    // =========================================================================
    // PHẦN 10: CÁC NGHIỆP VỤ TẠO MẤT THẺ VÀ ĐIỀU CHỈNH PHÍ TRỰC TIẾP
    // =========================================================================

    /**
     * =========================================================================
     * TẠO NHANH SỰ CỐ MẤT THẺ (CREATE LOST CARD INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Cho phép Nhân viên tạo nhanh sự cố mất thẻ cho xe đang đỗ và áp dụng tiền
     * phạt.
     * Bỏ qua các bước duyệt Phase 1, đi thẳng vào trạng thái phạt.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.reportLostCard`: API thứ 13, gọi khi khách làm
     * mất thẻ ngay tại trạm thu phí.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Kiểm tra loại xe hợp lệ và quyền sở hữu xe (nếu là khách tự tạo).
     * 2. Tìm phiên đỗ ACTIVE của xe.
     * 3. Kiểm tra xe đã có sự cố LOST_CARD nào chưa, tránh tạo trùng.
     * 4. Lấy mức phạt thẻ bị mất từ cấu hình hệ thống (PENALTY_LOST_CARD).
     * 5. Cộng mức phạt vào phiên đỗ.
     * 6. Tạo ticket mới, loại LOST_CARD, ưu tiên HIGH, trạng thái PENDING.
     * 7. Lưu DB và phát sóng cảnh báo đỏ (WebSocket).
     */
    @Transactional
    public IncidentTicketDTO createLostCardIncident(String plate, BigDecimal fee, String description,
            String uploadedDocUrl, String email, Long vehicleTypeId) {
        if (vehicleTypeId == null) {
            throw new IllegalArgumentException("Loại phương tiện không được để trống.");
        }
        verifyVehicleOwnership(plate, email);
        java.util.List<ParkingSession> activeSessions = sessionRepository
                .findByPlateAndVehicleTypeIdAndStatus(plate.trim().toUpperCase(), vehicleTypeId, "ACTIVE");
        ParkingSession session = activeSessions.isEmpty() ? null : activeSessions.get(0);

        if (session == null) {
            throw new IllegalArgumentException("Khong tim thay phien do xe ACTIVE cho bien so: " + plate);
        }

        boolean exists = incidentTicketRepository.existsBySessionIdAndIssueTypeAndStatusIn(session.getId(), "LOST_CARD",
                java.util.Arrays.asList("PENDING", "WAITING_CHECKOUT"));
        if (exists) {
            throw new IllegalArgumentException(
                    "Đã tồn tại một sự cố loại LOST_CARD đang chờ xử lý cho xe này trong phiên đỗ hiện tại!");
        }
        if (session.getVehicleType() != null && !session.getVehicleType().getId().equals(vehicleTypeId)) {
            throw new IllegalArgumentException(
                    "Biển số này thuộc về loại phương tiện khác trong hệ thống. Vui lòng kiểm tra lại loại xe.");
        }

        BigDecimal defaultFine = new BigDecimal("200000");
        try {
            defaultFine = new BigDecimal(systemConfigService.getConfigByKey("PENALTY_LOST_CARD").getConfigValue());
        } catch (Exception e) {
            log.warn("Could not find PENALTY_LOST_CARD config, using default 200000");
        }
        BigDecimal fineAmount = fee != null ? fee : defaultFine;
        session.setPenaltyFee(fineAmount);
        sessionRepository.save(session);

        com.pbms.modules.identity.domain.User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
        }

        IncidentTicket ticket = new IncidentTicket();
        ticket.setSession(session);
        ticket.setUser(user);
        ticket.setIssueType("LOST_CARD");
        ticket.setPriority("HIGH");
        ticket.setDescription(description != null ? description : "Bao mat the, tien phat: " + fineAmount);
        ticket.setStatus("PENDING");
        ticket.setFineAmount(fineAmount);
        ticket.setUploadedDocUrl(fileStorageService.storeBase64File(uploadedDocUrl));

        log.info("LOST_CARD incident created for plate: {}, fine: {}", plate, fineAmount);
        messagingTemplate.convertAndSend("/topic/alerts",
                "[MAT THE] Bien so " + plate + " da bao mat the. Phi phat: " + fineAmount.toPlainString() + " VND");

        return mapToDTO(saveAndBroadcast(ticket));
    }

    /**
     * =========================================================================
     * QUẢN LÝ ĐIỀU CHỈNH PHÍ ĐỖ XE TRỰC TIẾP (FEE ADJUSTMENT INCIDENT)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Ghi nhận log khi quản lý can thiệp trực tiếp để đổi mức phí đỗ xe
     * (thay đổi `parkingFee` của phiên đỗ). Tạo ra một sự cố dạng log tự động hoàn
     * tất.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.adjustFee`: Gọi khi Manager muốn sửa giá vé khẩn
     * cấp.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm phiên đỗ ACTIVE của biển số.
     * 2. Đổi giá vé `parkingFee` của phiên sang giá trị mới.
     * 3. Tạo ticket sự cố FEE_ADJUSTMENT để log lại lịch sử (Ai đổi, giá cũ, giá
     * mới, lý do).
     * 4. Set luôn trạng thái RESOLVED vì đây chỉ là log hành động.
     * 5. Lưu ticket, lưu phiên đỗ và phát sóng WebSocket.
     */
    @Transactional
    public IncidentTicketDTO adjustFeeIncident(String plate, BigDecimal liveFee, String reason, Long vehicleTypeId) {
        if (vehicleTypeId == null) {
            throw new IllegalArgumentException("Loại phương tiện không được để trống.");
        }
        java.util.List<ParkingSession> activeSessions = sessionRepository
                .findByPlateAndVehicleTypeIdAndStatus(plate.trim().toUpperCase(), vehicleTypeId, "ACTIVE");
        ParkingSession session = activeSessions.isEmpty() ? null : activeSessions.get(0);
        if (session == null) {
            throw new IllegalArgumentException("Khong tim thay phien do xe ACTIVE cho bien so: " + plate);
        }

        BigDecimal oldFee = session.getParkingFee();
        session.setParkingFee(liveFee);
        sessionRepository.save(session);

        String desc = String.format(
                "Manager dieu chinh phi. Bien so: %s | Phi cu: %s | Phi moi: %s VND | Ly do: %s",
                plate,
                oldFee != null ? oldFee.toPlainString() : "chua tinh",
                liveFee.toPlainString(),
                reason != null ? reason : "Khong co");

        IncidentTicket ticket = IncidentTicket.builder()
                .session(session)
                .issueType("FEE_ADJUSTMENT")
                .priority("MEDIUM")
                .description(desc)
                .status("RESOLVED")
                .fineAmount(liveFee)
                .resolvedAt(com.pbms.common.utils.TimeProvider.now())
                .resolutionNotes("[TU DONG] Gianh quyen can thiep phi boi Manager")
                .build();

        log.info("FEE_ADJUSTMENT incident: plate={}, newFee={}", plate, liveFee);
        return mapToDTO(saveAndBroadcast(ticket));
    }

    // =========================================================================
    // PHẦN 11: CHUYỂN ĐỔI DỮ LIỆU SỰ CỐ VÀ TRA CỨU XE (DTO MAPPING & CHECK PLATE)
    // =========================================================================

    /**
     * =========================================================================
     * CHUYỂN ĐỔI ENTITY SANG DTO KÈM BÁO GIÁ TRỰC TIẾP (MAP TO DTO)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Chuyển đổi đối tượng IncidentTicket sang DTO trả về cho Frontend.
     * Tự động kết nối với `GateOperationService.getCheckOutSessionInfo(...)` để
     * tính toán
     * báo giá thực tế hiện tại, số phút quá giờ, số tiền phạt và tạo JWT Checkout
     * Token.
     * 
     * AI GỌI HÀM NÀY:
     * - Gọi nội bộ ở cuối mọi hàm tạo, sửa, hoàn tất sự cố để trả dữ liệu chuẩn hóa
     * về FE.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Ánh xạ dữ liệu cơ bản của sự cố và thông tin phiên đỗ (Giờ vào, hình ảnh,
     * biển số).
     * 2. Dựa vào Status của sự cố để quy định Phase (1, 2, 3) cho UI Frontend xử
     * lý.
     * 3. Móc nối `GateOperationService` để tính tổng phí tại thời điểm hiện tại
     * (nếu sự cố đang mở)
     * hoặc thời điểm hoàn tất (nếu sự cố đã đóng).
     * 4. Đổ tất cả biểu phí (Phạt, vé, quá giờ, chiết khấu) vào IncidentTicketDTO.
     */
    private IncidentTicketDTO mapToDTO(IncidentTicket ticket) {
        int phase = 3;
        if ("PENDING".equals(ticket.getStatus()))
            phase = 1;
        else if ("WAITING_CHECKOUT".equals(ticket.getStatus()))
            phase = 2;

        ParkingSession session = ticket.getSession();
        String sessionTimeIn = null;
        String sessionPicIn = null;
        java.math.BigDecimal sessionParkingFee = null;
        String sessionVehicleType = null;
        Long sessionId = null;

        String sessionPicOut = null;
        String sessionSuggestedZone = null;
        String sessionPicInPlate = null;

        if (session != null) {
            sessionId = session.getId();
            sessionTimeIn = session.getTimeIn() != null ? session.getTimeIn().toString() : null;
            sessionPicIn = session.getPicInPanorama();
            sessionPicInPlate = session.getPicInFace();
            sessionPicOut = session.getPicOutPanorama();
            sessionParkingFee = session.getParkingFee();
            sessionVehicleType = session.getVehicleType() != null ? session.getVehicleType().getTypeName() : null;
            if (session.getSuggestedZoneId() != null) {
                sessionSuggestedZone = zoneRepository.findById(session.getSuggestedZoneId())
                        .map(zone -> zone.getZoneName())
                        .orElse(session.getSuggestedZoneId() == -1L ? "Khu vực tự do"
                                : "Zone " + session.getSuggestedZoneId());
            } else {
                sessionSuggestedZone = "Khu vực tự do";
            }
        }

        String customerType = null;
        Long durationMinutes = null;
        Long overtimeMinutes = null;
        java.math.BigDecimal expectedFee = null;
        java.math.BigDecimal overtimeFee = null;
        java.math.BigDecimal discountFee = null;
        java.math.BigDecimal sessionPenaltyFee = null;
        String checkoutToken = null;

        if (session != null) {
            java.time.LocalDateTime targetTime = null;
            if ("WAITING_CHECKOUT".equals(ticket.getStatus()) || "PENDING".equals(ticket.getStatus())) {
                targetTime = com.pbms.common.utils.TimeProvider.now();
            } else if ("RESOLVED".equals(ticket.getStatus()) || "REJECTED".equals(ticket.getStatus())) {
                targetTime = session.getTimeOut();
            }
            if (targetTime == null)
                targetTime = session.getTimeIn();
            if (targetTime == null)
                targetTime = com.pbms.common.utils.TimeProvider.now();

            try {
                com.pbms.modules.operation.service.GateOperationService gateOperationService = applicationContext
                        .getBean(com.pbms.modules.operation.service.GateOperationService.class);
                com.pbms.modules.operation.dto.CheckOutSessionInfoDTO checkoutInfo = gateOperationService
                        .getCheckOutSessionInfo(session, targetTime);
                customerType = checkoutInfo.getCustomerType();
                durationMinutes = checkoutInfo.getDurationMinutes();
                overtimeMinutes = checkoutInfo.getOvertimeMinutes();
                expectedFee = checkoutInfo.getExpectedFee();
                overtimeFee = checkoutInfo.getOvertimeFee();
                discountFee = checkoutInfo.getDiscountFee();
                sessionPenaltyFee = checkoutInfo.getFeePenalty();
                checkoutToken = checkoutInfo.getCheckoutToken();
            } catch (Exception e) {
                log.warn("Could not calculate checkout info for session: {}", session.getId());
            }
        }

        return IncidentTicketDTO.builder()
                .id(ticket.getId())
                .plateNumber(session != null ? session.getPlate() : null)
                .issueType(ticket.getIssueType())
                .priority(ticket.getPriority())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .fineAmount(ticket.getFineAmount())
                .resolutionNotes(ticket.getResolutionNotes())
                .resolutionImageUrl(ticket.getResolutionImageUrl())
                .resolvedAt(ticket.getResolvedAt())
                .createdAt(ticket.getCreatedAt())
                .uploadedDocUrl(ticket.getUploadedDocUrl())
                .staffEmail(ticket.getStaff() != null ? ticket.getStaff().getEmail() : null)
                .type(ticket.getIssueType())
                .phase(phase)
                .plate(session != null ? session.getPlate() : null)
                .time(ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : "")
                .sessionId(sessionId)
                .sessionTimeIn(sessionTimeIn)
                .sessionPicInPanorama(sessionPicIn)
                .sessionPicInPlate(sessionPicInPlate)
                .creatorEmail(ticket.getUser() != null ? ticket.getUser().getEmail() : null)
                .sessionPicOutPanorama(sessionPicOut)
                .sessionParkingFee(sessionParkingFee)
                .sessionVehicleType(sessionVehicleType)
                .vehicleTypeId(
                        session != null && session.getVehicleType() != null ? session.getVehicleType().getId() : null)
                .sessionSuggestedZone(sessionSuggestedZone)
                .baseFee(sessionParkingFee)
                .cancelType(ticket.getCancelType())
                .customerType(customerType)
                .durationMinutes(durationMinutes)
                .overtimeMinutes(overtimeMinutes)
                .expectedFee(expectedFee)
                .sessionPenaltyFee(sessionPenaltyFee)
                .overtimeFee(overtimeFee)
                .discountFee(discountFee)
                .checkoutToken(checkoutToken)
                .build();
    }

    /**
     * =========================================================================
     * TRA CỨU THÔNG TIN HOẠT ĐỘNG CỦA BIỂN SỐ XE (CHECK PLATE ACTIVE INFO)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Kiểm tra nhanh xem một biển số có đang trong bãi (ACTIVE session) hay không,
     * và có vé tháng hay không. Giúp FE gợi ý hoặc chặn các thao tác tạo sự cố
     * không hợp lệ.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.checkPlate`: Gọi từ ô nhập biển số xe khi tạo sự
     * cố thủ công.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tra cứu xem biển số có vé tháng ACTIVE không -> lưu vào cờ
     * `hasMonthlyTicket`.
     * 2. Tìm phiên đỗ xe ACTIVE cho biển số này theo loại xe.
     * 3. Nếu có phiên, lưu `isActive = true` và trả về loại phương tiện.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkPlateActiveInfo(String plate, Long vehicleTypeId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("isActive", false);
        result.put("hasMonthlyTicket", false);

        monthlyTicketRepository.findByPlateNumberAndStatus(plate.trim().toUpperCase(), "ACTIVE")
                .ifPresent(ticket -> result.put("hasMonthlyTicket", true));

        if (vehicleTypeId == null) {
            throw new IllegalArgumentException("Loại phương tiện không được để trống.");
        }
        java.util.List<ParkingSession> activeSessions = sessionRepository
                .findByPlateAndVehicleTypeIdAndStatus(plate.trim().toUpperCase(), vehicleTypeId, "ACTIVE");

        if (!activeSessions.isEmpty()) {
            ParkingSession session = activeSessions.get(0);
            result.put("isActive", true);
            result.put("vehicleType",
                    session.getVehicleType() != null ? session.getVehicleType().getTypeName() : "Unknown");
        }
        return result;
    }

    /**
     * =========================================================================
     * TRA CỨU THÔNG TIN HOẠT ĐỘNG KẾT HỢP BIỂN SỐ XE VÀ MÃ THẺ RFID
     * (CHECK PLATE AND RFID ACTIVE INFO)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Tương tự `checkPlateActiveInfo` nhưng kiểm tra chéo luôn cả mã RFID để xác
     * minh
     * thẻ có khớp với biển số đang đỗ hay không. Tránh râu ông nọ cắm cằm bà kia.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.checkPlateAndRfid`: Dùng khi quẹt thẻ báo lỗi
     * nhưng cần check kỹ.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tra cứu vé tháng của biển số.
     * 2. Tìm phiên đỗ xe ACTIVE của biển số.
     * 3. So khớp mã RFID của thẻ gửi xe trong phiên đỗ với mã RFID được cung cấp.
     * 4. Nếu khớp hoàn toàn, trả về trạng thái hợp lệ.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkPlateAndRfidActiveInfo(String plate, String rfid, Long vehicleTypeId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("isActive", false);
        result.put("hasMonthlyTicket", false);

        monthlyTicketRepository.findByPlateNumberAndStatus(plate.trim().toUpperCase(), "ACTIVE")
                .ifPresent(ticket -> result.put("hasMonthlyTicket", true));

        if (vehicleTypeId == null) {
            throw new IllegalArgumentException("Loại phương tiện không được để trống.");
        }
        java.util.List<ParkingSession> activeSessions = sessionRepository
                .findByPlateAndVehicleTypeIdAndStatus(plate.trim().toUpperCase(), vehicleTypeId, "ACTIVE");

        activeSessions.stream()
                .filter(session -> session.getRfidCard() != null &&
                        (session.getRfidCard().getCardCode().equalsIgnoreCase(rfid.trim()) ||
                                session.getRfidCard().getCardId().equalsIgnoreCase(rfid.trim())))
                .findFirst()
                .ifPresent(session -> {
                    result.put("isActive", true);
                    result.put("vehicleType",
                            session.getVehicleType() != null ? session.getVehicleType().getTypeName() : "Unknown");
                });
        return result;
    }

    /**
     * =========================================================================
     * GIẢI QUYẾT KHIẾU NẠI MỨC PHÍ (RESOLVE FEE DISPUTE)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Áp dụng số tiền giảm giá (Discount) vào phiên đỗ và đóng sự cố khiếu nại mức
     * phí.
     * 
     * AI GỌI HÀM NÀY:
     * - `IncidentTicketController.resolveFeeDispute`: Gọi từ tính năng hòa giải
     * giảm tiền cho khách.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tìm sự cố, kiểm tra phải đang ở Phase 2 (WAITING_CHECKOUT) và loại
     * FEE_DISPUTE.
     * 2. Cập nhật trường `discount` của phiên đỗ bằng số tiền được giảm.
     * 3. Đánh dấu trạng thái sự cố là RESOLVED.
     * 4. Lưu ảnh và ghi chú xử lý.
     * 5. Lưu DB và phát sóng.
     */
    @Transactional
    public void resolveFeeDispute(Long id, java.math.BigDecimal discountAmount, String resolutionNotes,
            String resolutionImageUrl) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is not in phase 2");
        }

        if (!"FEE_DISPUTE".equals(ticket.getIssueType())) {
            throw new IllegalStateException("This endpoint is only for FEE_DISPUTE");
        }

        ParkingSession session = ticket.getSession();
        if (session != null) {
            session.setDiscount(discountAmount);
            sessionRepository.save(session);
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes(resolutionNotes);
        if (resolutionImageUrl != null && !resolutionImageUrl.isBlank()) {
            ticket.setResolutionImageUrl(fileStorageService.storeBase64File(resolutionImageUrl));
        }
        saveAndBroadcast(ticket);
    }

    /**
     * =========================================================================
     * HÀM NỘI BỘ: LƯU SỰ CỐ VÀ PHÁT SÓNG WEBSOCKET (SAVE & BROADCAST)
     * =========================================================================
     * MỤC ĐÍCH NGHIỆP VỤ:
     * Hàm dùng chung để lưu đối tượng vào database và đồng thời thông báo
     * qua WebSockets cho toàn bộ client biết (để tự reload lại bảng dữ liệu).
     * 
     * AI GỌI HÀM NÀY:
     * - Tất cả các hàm thao tác thay đổi trạng thái của Ticket.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Lưu Ticket vào `incidentTicketRepository`.
     * 2. Bắn tin nhắn qua kênh `/topic/alerts`. Nếu có lỗi kết nối thì log lại lỗi
     * chứ không sập app.
     * 3. Trả về đối tượng Ticket đã lưu.
     */
    private IncidentTicket saveAndBroadcast(IncidentTicket ticket) {
        IncidentTicket saved = incidentTicketRepository.save(ticket);
        try {
            messagingTemplate.convertAndSend("/topic/alerts",
                    "{\"type\":\"INCIDENT_UPDATE\",\"message\":\"Incident list has been updated.\"}");
        } catch (Exception e) {
            log.error("Failed to broadcast incident update", e);
        }
        return saved;
    }
}
