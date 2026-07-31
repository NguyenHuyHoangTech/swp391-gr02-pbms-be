/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Lớp Service xử lý toàn bộ logic nghiệp vụ liên quan đến vé tháng (Monthly Ticket).
 *               Bao gồm đăng ký mới, gia hạn, đổi biển số, cảnh báo bãi đầy và tích hợp thanh toán/email.
 * @Dependencies: 
 * - MonthlyTicketRepository, VehicleRepository, ParkingSessionRepository
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.operation.domain.MonthlyTicket;
import com.pbms.modules.operation.dto.MonthlyTicketDTO;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import com.pbms.modules.operation.repository.VehicleTypeRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyTicketService {

    private final MonthlyTicketRepository monthlyTicketRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final com.pbms.modules.operation.repository.ParkingSessionRepository parkingSessionRepository;
    private final com.pbms.modules.identity.repository.UserRepository userRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final com.pbms.modules.operation.repository.VehicleRepository vehicleRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final com.pbms.modules.infrastructure.repository.SlotRepository slotRepository;
    private final com.pbms.common.service.EmailService emailService;
    private final com.pbms.modules.operation.repository.ReservationRepository reservationRepository;
    private final com.pbms.modules.finance.repository.TransactionRepository transactionRepository;
    private final com.pbms.modules.finance.repository.PricingPolicyRepository pricingPolicyRepository;
    private final com.pbms.modules.operation.repository.StaffWorkSessionRepository staffWorkSessionRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Lấy danh sách tất cả vé tháng (kèm tính toán trạng thái động).
     * <p>
     * Mã giả:
     * 1. Lấy thông tin user đăng nhập. Nếu là Customer thì chỉ lấy vé của họ.
     * 2. Lấy toàn bộ vé tháng từ DB.
     * 3. Lặp qua từng vé:
     *    - Nếu vé đang ACTIVE nhưng đã quá hạn -> đổi thành EXPIRED (trên UI).
     *    - Nếu vé đang ACTIVE và còn <= 7 ngày -> đổi thành EXPIRING_SOON.
     *    - Kiểm tra xem xe có đang đỗ trong bãi không bằng cách tìm ParkingSession ACTIVE.
     * 4. Build thành đối tượng MonthlyTicketDTO và trả về list.
     * </p>
     */
    @Transactional(readOnly = true)
    public List<MonthlyTicketDTO> getAllTickets() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        String currentEmail = auth != null ? auth.getName() : null;
        boolean isCustomer = auth != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))
                && auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));

        List<MonthlyTicket> tickets;
        if (isCustomer && currentEmail != null) {
            tickets = monthlyTicketRepository.findAll().stream()
                    .filter(t -> t.getUser() != null && currentEmail.equals(t.getUser().getEmail()))
                    .collect(Collectors.toList());
        } else {
            tickets = monthlyTicketRepository.findAll();
        }
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();

        return tickets.stream().map(ticket -> {
            String derivedStatus = ticket.getStatus();

            if ("ACTIVE".equals(derivedStatus)) {
                if (ticket.getValidUntil().isBefore(now)) {
                    derivedStatus = "EXPIRED";
                } else if (ChronoUnit.DAYS.between(now, ticket.getValidUntil()) <= 7) {
                    derivedStatus = "EXPIRING_SOON";
                }
            }

            boolean hasBeenUsed = parkingSessionRepository.existsByPlateAndTimeInGreaterThanEqual(ticket.getPlateNumber(),
                    ticket.getValidFrom());

            boolean inParkingLot = false;
            List<com.pbms.modules.operation.domain.ParkingSession> sessions = parkingSessionRepository
                    .findByPlateOrderByTimeInDesc(ticket.getPlateNumber());
            if (!sessions.isEmpty()
                    && ("ACTIVE".equals(sessions.get(0).getStatus()) || "LOCKED".equals(sessions.get(0).getStatus()))) {
                inParkingLot = true;
            }

            String rfidCode = null;
            if (inParkingLot && !sessions.isEmpty() && sessions.get(0).getRfidCard() != null) {
                rfidCode = sessions.get(0).getRfidCard().getCardCode();
            }

            return MonthlyTicketDTO.builder()
                    .id("MP-" + ticket.getId())
                    .user(ticket.getUser() != null ? ticket.getUser().getFullName() : "Guest")
                    .email(ticket.getUser() != null ? ticket.getUser().getEmail() : "")
                    .phone("") // phone doesn't exist on User
                    .plate(ticket.getPlateNumber())
                    .type(ticket.getVehicleType() != null ? ticket.getVehicleType().getTypeName() : "N/A")
                    .vehicleTypeId(ticket.getVehicleType() != null ? ticket.getVehicleType().getId() : null)
                    .status(derivedStatus)
                    .startDate(ticket.getValidFrom().format(FORMATTER))
                    .endDate(ticket.getValidUntil().format(FORMATTER))
                    .hasBeenUsed(hasBeenUsed)
                    .inParkingLot(inParkingLot)
                    .rfid(rfidCode)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Cronjob (chạy lúc 1:00 AM) để quét và vô hiệu hóa vé hết hạn.
     * <p>
     * Mã giả:
     * 1. Tìm các vé tháng có thời gian hết hạn (validUntil) trước thời gian hiện tại.
     * 2. Gửi email thông báo hết hạn (EXPIRED) cho từng user có vé.
     * 3. Cập nhật trạng thái các vé đó thành EXPIRED trong database.
     * </p>
     */
    @Scheduled(cron = "0 0 1 * * ?") // Runs at 1:00 AM every day
    @Transactional
    public void expireMonthlyTickets() {
        log.info("Running expireMonthlyTickets cronjob...");
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();

        // 1. Fetch tickets that are about to be expired
        List<MonthlyTicket> expiringTickets = monthlyTicketRepository.findTicketsToProcessExpiration(now);
        for (MonthlyTicket ticket : expiringTickets) {
            if (ticket.getUser() != null) {
                sendTicketEmail(ticket.getUser(), ticket, "EXPIRED");
            }
        }
        log.info("Sent expiration emails for {} tickets.", expiringTickets.size());

        // 2. Mark them as EXPIRED
        int expiredCount = monthlyTicketRepository.expirePastTickets(now);
        log.info("Expired {} monthly tickets.", expiredCount);
    }

    /**
     * Xử lý sự kiện tua nhanh thời gian (IoT Simulator).
     * <p>
     * Mã giả:
     * 1. Lấy thời gian mô phỏng cũ và mới.
     * 2. Kiểm tra xem khoảng thời gian tua có vượt qua mốc 1:00 AM không (hasCrossedTime).
     * 3. Nếu có, kích hoạt thủ công hàm expireMonthlyTickets().
     * </p>
     */
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime oldTime = event.getOldSimulatedTime();
        LocalDateTime newTime = event.getNewSimulatedTime();
        if (hasCrossedTime(oldTime, newTime, 1, 0)) {
            expireMonthlyTickets();
        }
    }

    /**
     * Kiểm tra xem khoảng thời gian (từ oldTime đến newTime) có đi qua một giờ nhất định không.
     */
    private boolean hasCrossedTime(LocalDateTime oldTime, LocalDateTime newTime, int targetHour, int targetMinute) {
        if (oldTime == null || newTime == null || !oldTime.isBefore(newTime)) return false;

        LocalDateTime target = oldTime.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (!target.isAfter(oldTime)) {
            target = target.plusDays(1);
        }

        return !target.isAfter(newTime);
    }

    /**
     * Kiểm tra xem xe (theo biển số) có đang đỗ trong bãi hay không.
     * <p>
     * Mã giả:
     * 1. Lấy danh sách ParkingSession của biển số.
     * 2. Nếu có phiên nào đang ở trạng thái ACTIVE hoặc LOCKED -> trả về true (đang trong bãi).
     * </p>
     */
    private boolean isVehicleInside(String plate) {
        if (plate == null || plate.isBlank())
            return false;
        java.util.List<com.pbms.modules.operation.domain.ParkingSession> sessions = parkingSessionRepository
                .findByPlateOrderByTimeInDesc(plate);
        for (com.pbms.modules.operation.domain.ParkingSession s : sessions) {
            if ("ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Xác thực dữ liệu đầu vào trước khi tạo vé tháng mới.
     * <p>
     * Mã giả:
     * 1. Kiểm tra biển số, loại xe không được trống.
     * 2. Kiểm tra xe có đang ở trong bãi không -> Nếu có, từ chối tạo vé để tránh lỗi logic giá tiền.
     * 3. Kiểm tra xe có bị Blacklist hoặc đăng ký sai loại xe không.
     * 4. Kiểm tra xe đã có vé tháng ACTIVE chưa -> Có rồi thì từ chối.
     * 5. Kiểm tra xe có đang có đơn đặt chỗ trước (PENDING) không -> Có thì từ chối.
     * </p>
     */
    public void validateCreateTicket(Map<String, Object> payload) {
        String plate = (String) payload.get("plateNumber");
        if (plate == null || plate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate required.");
        }

        Long vehicleTypeId = payload.get("vehicleTypeId") != null
                ? Long.parseLong(payload.get("vehicleTypeId").toString())
                : null;
        if (vehicleTypeId == null) {
            throw new IllegalArgumentException("Vehicle type required.");
        }

        if (isVehicleInside(plate)) {
            throw new IllegalArgumentException("Vehicle must exit the lot before registering a pass.");
        }

        com.pbms.modules.operation.domain.Vehicle vehicle = vehicleRepository.findByPlateNumber(plate).orElse(null);
        if (vehicle != null) {
            if (Boolean.TRUE.equals(vehicle.getIsBlacklisted())) {
                throw new IllegalArgumentException("Vehicle is blacklisted.");
            }
            if (vehicle.getVehicleType() != null && !vehicle.getVehicleType().getId().equals(vehicleTypeId)) {
                throw new IllegalArgumentException("Plate registered with another vehicle type.");
            }
        }

        boolean hasActiveTicket = monthlyTicketRepository.findByPlateNumberAndStatus(plate, "ACTIVE").isPresent();
        if (hasActiveTicket) {
            throw new IllegalArgumentException("Vehicle already has an active pass.");
        }

        boolean hasPendingReservation = !reservationRepository.findByVehicle_PlateNumberAndStatus(plate, "PENDING").isEmpty();
        if (hasPendingReservation) {
            throw new IllegalArgumentException("Vehicle has a pending booking.");
        }
    }

    /**
     * Xác thực trước khi gia hạn vé.
     * <p>
     * Mã giả:
     * 1. Tìm vé theo ID.
     * 2. Kiểm tra xem vé có đang bị quá hạn VÀ xe đang ở trong bãi không.
     *    - Nếu có: Bắt buộc xe phải ra khỏi bãi (tính phí vãng lai) rồi mới cho gia hạn, để tránh xung đột hệ thống.
     * </p>
     */
    public void validateRenewTicket(Long id, int durationMonths) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pass not found."));

        boolean isExpired = "EXPIRED".equals(ticket.getStatus())
                || ticket.getValidUntil().isBefore(com.pbms.common.utils.TimeProvider.now());
        if (isExpired && isVehicleInside(ticket.getPlateNumber())) {
            throw new IllegalArgumentException("Vehicle must exit the lot before renewing.");
        }
    }

    /**
     * Tạo mới một vé tháng.
     * <p>
     * Mã giả:
     * 1. Validate dữ liệu (gọi validateCreateTicket).
     * 2. Lấy thông tin user hiện tại.
     * 3. Cập nhật hoặc tạo mới thông tin Xe (Vehicle) trong hệ thống.
     * 4. Khởi tạo vé tháng (từ thời điểm hiện tại đến N tháng sau), trạng thái ACTIVE.
     * 5. Lưu vé vào DB.
     * 6. Ghi nhận giao dịch thanh toán (recordTransaction).
     * 7. Kiểm tra ngưỡng sức chứa vé tháng để cảnh báo quá tải (checkMonthlyThreshold).
     * 8. Gửi email xác nhận đăng ký thành công.
     * 9. Trả về DTO hiển thị.
     * </p>
     */
    @Transactional
    public MonthlyTicketDTO createTicket(Map<String, Object> payload) {
        validateCreateTicket(payload);

        String plate = (String) payload.get("plateNumber");

        Long vehicleTypeId = payload.get("vehicleTypeId") != null
                ? Long.parseLong(payload.get("vehicleTypeId").toString())
                : null;
        int months = 1;
        if (payload.get("durationMonths") != null) {
            months = Integer.parseInt(payload.get("durationMonths").toString());
        } else if (payload.get("duration") != null) {
            months = Integer.parseInt(payload.get("duration").toString());
        }

        com.pbms.modules.operation.domain.VehicleType vt = null;
        if (vehicleTypeId != null) {
            vt = vehicleTypeRepository.findById(vehicleTypeId).orElse(null);
        }

        // Get Current User
        com.pbms.modules.identity.domain.User currentUser = null;
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not get current user context", e);
        }

        // Fetch or create Vehicle first
        com.pbms.modules.operation.domain.Vehicle existingVehicle = vehicleRepository.findByPlateNumber(plate).orElse(null);
        if (existingVehicle != null && currentUser != null) {
            if (existingVehicle.getUser() == null || !existingVehicle.getUser().getId().equals(currentUser.getId())) {
                existingVehicle.setUser(currentUser);
                vehicleRepository.save(existingVehicle);
                log.info("Overwritten ownership of vehicle {} to user {}", plate, currentUser.getEmail());
            }
        } else if (existingVehicle == null) {
            existingVehicle = com.pbms.modules.operation.domain.Vehicle.builder()
                    .plateNumber(plate)
                    .vehicleType(vt)
                    .user(currentUser)
                    .build();
            existingVehicle = vehicleRepository.save(existingVehicle);
        }

        MonthlyTicket ticket = MonthlyTicket.builder()
                .validFrom(com.pbms.common.utils.TimeProvider.now())
                .validUntil(com.pbms.common.utils.TimeProvider.now().plusMonths(months))
                .status("ACTIVE")

                .user(currentUser)
                .plateNumber(plate)
                .vehicleType(vt)
                .build();

        monthlyTicketRepository.save(ticket);

        recordTransaction(ticket, months, payload.get("paymentMethod") != null ? payload.get("paymentMethod").toString() : null, payload.get("paymentOrderId") != null ? Long.parseLong(payload.get("paymentOrderId").toString()) : null);



        try {
            checkMonthlyThreshold();
        } catch (Exception e) {
            log.error("Failed to check monthly threshold", e);
        }

        if (currentUser != null && currentUser.getEmail() != null) {
            sendTicketEmail(currentUser, ticket, "CREATE");
        }

        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlateNumber())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    /**
     * Gia hạn vé tháng.
     * <p>
     * Mã giả:
     * 1. Gọi validateRenewTicket để kiểm tra.
     * 2. Tính toán ngày hết hạn mới:
     *    - Nếu vé chưa hết hạn: ngày mới = ngày hết hạn cũ + N tháng.
     *    - Nếu vé đã hết hạn: ngày mới = ngày hiện tại + N tháng, đồng thời chuyển trạng thái thành ACTIVE.
     * 3. Lưu vào DB và ghi nhận giao dịch thanh toán.
     * 4. Gửi email xác nhận gia hạn thành công.
     * 5. Trả về DTO hiển thị.
     * </p>
     */
    @Transactional
    public MonthlyTicketDTO renewTicket(Long id, int durationMonths, String paymentMethod, Long paymentOrderId) {
        validateRenewTicket(id, durationMonths);

        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        LocalDateTime newEndDate;
        if (ticket.getValidUntil().isAfter(com.pbms.common.utils.TimeProvider.now())) {
            newEndDate = ticket.getValidUntil().plusMonths(durationMonths);
        } else {
            newEndDate = com.pbms.common.utils.TimeProvider.now().plusMonths(durationMonths);
            ticket.setValidFrom(com.pbms.common.utils.TimeProvider.now());
            ticket.setStatus("ACTIVE");
        }
        ticket.setValidUntil(newEndDate);
        monthlyTicketRepository.save(ticket);

        recordTransaction(ticket, durationMonths, paymentMethod, paymentOrderId);

        if (ticket.getUser() != null && ticket.getUser().getEmail() != null) {
            sendTicketEmail(ticket.getUser(), ticket, "RENEW");
        }

        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlateNumber())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    /**
     * Đổi biển số xe của vé tháng (Chỉ áp dụng nếu vé chưa từng sử dụng).
     * <p>
     * Mã giả:
     * 1. Tìm vé tháng.
     * 2. Kiểm tra xe cũ đã từng vào bãi bằng vé này chưa (thời gian vào >= lúc mua vé).
     * 3. Nếu đã dùng -> không cho đổi biển.
     * 4. Nếu chưa dùng -> Cập nhật biển số ở cả bảng Vehicle và bảng MonthlyTicket.
     * </p>
     */
    @Transactional
    public MonthlyTicketDTO updateTicketPlate(Long id, String newPlate) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pass not found."));

        if (newPlate == null || newPlate.isBlank()) {
            throw new IllegalArgumentException("New plate required.");
        }

        boolean hasBeenUsed = parkingSessionRepository.existsByPlateAndTimeInGreaterThanEqual(ticket.getPlateNumber(),
                ticket.getValidFrom());
        if (hasBeenUsed) {
            throw new IllegalStateException("Pass already used, cannot change plate.");
        }

        String oldPlate = ticket.getPlateNumber();
        ticket.setPlateNumber(newPlate);

        vehicleRepository.findByPlateNumber(oldPlate).ifPresent(v -> {
            v.setPlateNumber(newPlate);
            vehicleRepository.save(v);
        });
        monthlyTicketRepository.save(ticket);

        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlateNumber())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    /**
     * Kiểm tra sức chứa khu vực vé tháng và bắn cảnh báo nếu vượt ngưỡng.
     * <p>
     * Mã giả:
     * 1. Lấy tổng số lượng slot được phân bổ cho vé tháng (Function = MONTHLY).
     * 2. Lấy tổng số lượng vé tháng đang ACTIVE.
     * 3. Tính tỷ lệ % = (Tổng số vé ACTIVE / Tổng Slot) * 100.
     * 4. Nếu tỷ lệ > 90% (hoặc config) -> Bắn sự kiện qua Websocket (/topic/manager-alerts) báo cho Quản lý.
     * </p>
     */
    private void checkMonthlyThreshold() {
        try {
            int threshold = 90; // Default
            String configVal = systemConfigService.getConfigByKey("MONTHLY_TICKET_ALERT_THRESHOLD").getConfigValue();
            if (configVal != null && !configVal.isBlank()) {
                threshold = Integer.parseInt(configVal);
            }

            long totalMonthlySlots = slotRepository.countByZone_FunctionType("MONTHLY");
            if (totalMonthlySlots == 0)
                return;

            long totalActiveTickets = monthlyTicketRepository.countByStatus("ACTIVE");

            double currentPercentage = ((double) totalActiveTickets / totalMonthlySlots) * 100;

            if (currentPercentage > threshold) {
                log.warn("Monthly ticket threshold exceeded! Currently at {}% (Active: {}, Slots: {})",
                        String.format("%.1f", currentPercentage), totalActiveTickets, totalMonthlySlots);

                messagingTemplate.convertAndSend("/topic/manager-alerts",
                        (Object) Map.of(
                                "type", "MONTHLY_ZONE_OVERLOAD",
                                "message",
                                String.format(
                                        "Warning: The number of registered monthly tickets (%.1f%%) has exceeded the %d%% threshold of total Monthly Zone slots. Please consider expanding the Monthly Zone.",
                                        currentPercentage, threshold),
                                "activeTickets", totalActiveTickets,
                                "totalSlots", totalMonthlySlots));
            }
        } catch (Exception e) {
            log.error("Error in checkMonthlyThreshold", e);
        }
    }

    /**
     * Gửi email liên quan đến vé tháng cho người dùng.
     * <p>
     * Hỗ trợ 3 loại email (type):
     * - CREATE: Tạo mới thành công.
     * - RENEW: Gia hạn thành công.
     * - EXPIRED: Vé đã hết hạn.
     * Build nội dung HTML dạng bảng hiển thị thông tin vé rồi gửi đi.
     * </p>
     */
    private void sendTicketEmail(com.pbms.modules.identity.domain.User user, MonthlyTicket ticket, String type) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

        String subject = "";
        String htmlBody = "";
        String endDateStr = ticket.getValidUntil().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String startDateStr = ticket.getValidFrom().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String commonStyles = "font-family: Arial, sans-serif; color: #333; line-height: 1.6;";
        String tableStyles = "border-collapse: collapse; width: 100%; margin-top: 15px;";
        String thTdStyles = "border: 1px solid #ddd; padding: 8px; text-align: left;";

        String detailsHtml = "<table style='" + tableStyles + "'>" +
                "<tr><th style='" + thTdStyles + "'>License Plate</th><td style='" + thTdStyles + "'>" + ticket.getPlateNumber() + "</td></tr>" +
                "<tr><th style='" + thTdStyles + "'>Vehicle Type</th><td style='" + thTdStyles + "'>" + (ticket.getVehicleType() != null ? ticket.getVehicleType().getTypeName() : "N/A") + "</td></tr>" +
                "<tr><th style='" + thTdStyles + "'>Start Date</th><td style='" + thTdStyles + "'>" + startDateStr + "</td></tr>" +
                "<tr><th style='" + thTdStyles + "'>End Date</th><td style='" + thTdStyles + "'><strong>" + endDateStr + "</strong></td></tr>" +
                "</table>";

        if ("CREATE".equals(type)) {
            subject = "Successful Monthly Pass Registration - PBMS";
            htmlBody = "<div style='" + commonStyles + "'>" +
                    "<h2 style='color: #4CAF50;'>Monthly Pass Registered Successfully!</h2>" +
                    "<p>Hello " + user.getFullName() + ",</p>" +
                    "<p>Thank you for registering a monthly pass with PBMS. Here are your pass details:</p>" +
                    detailsHtml +
                    "<p style='margin-top: 20px;'>We hope you have a great experience with us!</p>" +
                    "</div>";
        } else if ("RENEW".equals(type)) {
            subject = "Successful Monthly Pass Renewal - PBMS";
            htmlBody = "<div style='" + commonStyles + "'>" +
                    "<h2 style='color: #2196F3;'>Monthly Pass Renewed Successfully!</h2>" +
                    "<p>Hello " + user.getFullName() + ",</p>" +
                    "<p>Your monthly pass has been successfully renewed. Here are the updated details:</p>" +
                    detailsHtml +
                    "<p style='margin-top: 20px;'>Thank you for continuing to use PBMS!</p>" +
                    "</div>";
        } else if ("EXPIRED".equals(type)) {
            subject = "Notification: Your Monthly Pass has Expired - PBMS";
            htmlBody = "<div style='" + commonStyles + "'>" +
                    "<h2 style='color: #f44336;'>Monthly Pass Expired</h2>" +
                    "<p>Hello " + user.getFullName() + ",</p>" +
                    "<p>We would like to inform you that your monthly pass expired on <strong>" + endDateStr + "</strong>.</p>" +
                    detailsHtml +
                    "<p style='margin-top: 20px;'>The system has automatically revoked the monthly pass access for this vehicle. Please renew your pass as soon as possible to avoid service disruption.</p>" +
                    "<p><a href='http://localhost:5173/customer/my-parking?tab=monthly' style='display: inline-block; padding: 10px 20px; background-color: #2196F3; color: white; text-decoration: none; border-radius: 5px;'>Renew Now</a></p>" +
                    "</div>";
        }

        emailService.sendHtmlEmail(user.getEmail(), subject, htmlBody);
    }

    /**
     * Ghi nhận giao dịch (doanh thu) vào hệ thống khi thanh toán vé tháng.
     * <p>
     * Mã giả:
     * 1. Lấy giá vé tháng từ bảng PricingPolicy theo loại xe.
     * 2. Tính số tiền = Giá vé * Số tháng.
     * 3. Tạo mới Transaction, gán cho phiên làm việc (WorkSession) của nhân viên nếu là tiền mặt.
     * 4. Lưu vào cơ sở dữ liệu.
     * </p>
     */
    private void recordTransaction(MonthlyTicket ticket, int durationMonths, String paymentMethod, Long paymentOrderId) {
        String method = paymentMethod != null ? paymentMethod : "CASH";

        com.pbms.modules.finance.domain.PricingPolicy policy = pricingPolicyRepository.findByVehicleTypeIdAndStatus(ticket.getVehicleType().getId(), "ACTIVE")
                .orElse(null);

        java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
        if (policy != null && policy.getMonthlyRate() != null) {
            amount = policy.getMonthlyRate().multiply(new java.math.BigDecimal(durationMonths));
        }

        com.pbms.modules.identity.domain.StaffWorkSession currentSession = null;
        if ("CASH".equalsIgnoreCase(method)) {
            try {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getName() != null) {
                    com.pbms.modules.identity.domain.User u = userRepository.findByEmail(auth.getName()).orElse(null);
                    if (u != null) {
                        currentSession = staffWorkSessionRepository.findByStaffIdAndStatus(u.getId(), "ACTIVE").orElse(null);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve active work session for cash transaction", e);
            }
        }

        com.pbms.modules.finance.domain.PaymentOrder po = null;
        if (paymentOrderId != null) {
            po = new com.pbms.modules.finance.domain.PaymentOrder();
            po.setId(paymentOrderId);
        }

        com.pbms.modules.finance.domain.Transaction tx = com.pbms.modules.finance.domain.Transaction.builder()
                .monthlyTicket(ticket)
                .workSession(currentSession)
                .paymentOrder(po)
                .amount(amount)
                .paymentMethod(method)
                .status("SUCCESS")
                .transactionReference("TXN-MT-" + ticket.getId() + "-" + com.pbms.common.utils.TimeProvider.now().toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
                .build();
        transactionRepository.save(tx);
        log.info("Recorded Monthly Ticket Transaction: {} via {}", amount, method);
    }
}
