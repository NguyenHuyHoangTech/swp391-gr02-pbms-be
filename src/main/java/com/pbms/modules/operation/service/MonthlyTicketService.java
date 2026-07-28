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

    @Transactional(readOnly = true)
    /**
     * Lấy danh sách toàn bộ vé tháng.
     * Logic mã giả:
     * 1. Lấy thông tin user hiện tại từ Security Context.
     * 2. Nếu là khách hàng (ROLE_CUSTOMER), lọc danh sách vé chỉ thuộc về email của người đó.
     * 3. Nếu là Admin/Staff, lấy toàn bộ danh sách vé.
     * 4. Lặp qua danh sách vé, kiểm tra thời hạn (hết hạn hay sắp hết hạn) và cập nhật trạng thái tạm thời.
     * 5. Kiểm tra vé đã từng được sử dụng chưa và xe có đang nằm trong bãi không.
     * 6. Trả về danh sách DTO cho FE.
     */
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

    @Scheduled(cron = "0 0 1 * * ?") // Runs at 1:00 AM every day
    @Transactional
    /**
     * Tác vụ tự động (Cron job) chạy vào 1h sáng mỗi ngày để xử lý vé hết hạn.
     * Logic mã giả:
     * 1. Truy vấn các vé có thời hạn (validUntil) nhỏ hơn thời điểm hiện tại và trạng thái là ACTIVE.
     * 2. Lặp qua danh sách vé hết hạn, gửi email thông báo (EXPIRED) cho từng chủ xe.
     * 3. Cập nhật trạng thái của tất cả vé hết hạn trong DB thành EXPIRED thông qua một câu lệnh UPDATE duy nhất (tối ưu hiệu năng).
     */
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

    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime oldTime = event.getOldSimulatedTime();
        LocalDateTime newTime = event.getNewSimulatedTime();
        if (hasCrossedTime(oldTime, newTime, 1, 0)) {
            expireMonthlyTickets();
        }
    }

    private boolean hasCrossedTime(LocalDateTime oldTime, LocalDateTime newTime, int targetHour, int targetMinute) {
        if (oldTime == null || newTime == null || !oldTime.isBefore(newTime)) return false;
        
        LocalDateTime target = oldTime.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0);
        if (!target.isAfter(oldTime)) {
            target = target.plusDays(1);
        }

        return !target.isAfter(newTime);
    }

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
     * Xác thực các điều kiện nghiệp vụ trước khi tạo vé tháng mới.
     * Logic mã giả:
     * 1. Đảm bảo biển số xe và loại xe không bị trống.
     * 2. Kiểm tra xe có đang nằm trong bãi hay không, nếu có thì chặn không cho đăng ký.
     * 3. Kiểm tra xe có nằm trong danh sách đen (Blacklist) hay không.
     * 4. Kiểm tra xe đã có vé tháng nào đang ACTIVE không, nếu có thì không cho tạo thêm.
     * 5. Kiểm tra số lượng vé tháng đang ACTIVE trong bãi có vượt ngưỡng cho phép (THRESHOLD) hay không.
     */
    public void validateCreateTicket(Map<String, Object> payload) {
        String plate = (String) payload.get("plateNumber");
        if (plate == null || plate.trim().isEmpty()) {
            throw new IllegalArgumentException("Biển số xe không được để trống.");
        }

        Long vehicleTypeId = payload.get("vehicleTypeId") != null
                ? Long.parseLong(payload.get("vehicleTypeId").toString())
                : null;
        if (vehicleTypeId == null) {
            throw new IllegalArgumentException("Loại phương tiện không được để trống.");
        }

        if (isVehicleInside(plate)) {
            throw new IllegalArgumentException("Cannot register a monthly ticket because the vehicle with plate "
                    + plate + " is currently inside the parking lot.");
        }

        com.pbms.modules.operation.domain.Vehicle vehicle = vehicleRepository.findByPlateNumber(plate).orElse(null);
        if (vehicle != null) {
            if (Boolean.TRUE.equals(vehicle.getIsBlacklisted())) {
                throw new IllegalArgumentException(
                        "Cannot register a monthly ticket because the vehicle is in the Blacklist.");
            }
            if (vehicle.getVehicleType() != null && !vehicle.getVehicleType().getId().equals(vehicleTypeId)) {
                throw new IllegalArgumentException(
                        "Biển số này đã được đăng ký với loại phương tiện khác trong hệ thống.");
            }
        }
        
        boolean hasActiveTicket = monthlyTicketRepository.findByPlateNumberAndStatus(plate, "ACTIVE").isPresent();
        if (hasActiveTicket) {
            throw new IllegalArgumentException("Phương tiện này đã có vé tháng đang hoạt động. Vui lòng kiểm tra lại.");
        }
        
        boolean hasPendingReservation = !reservationRepository.findByVehicle_PlateNumberAndStatus(plate, "PENDING").isEmpty();
        if (hasPendingReservation) {
            throw new IllegalArgumentException("Phương tiện này đang có lịch đặt chỗ trước chờ xử lý. Không thể đăng ký vé tháng đè lên.");
        }
    }

    public void validateRenewTicket(Long id, int durationMonths) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        boolean isExpired = "EXPIRED".equals(ticket.getStatus())
                || ticket.getValidUntil().isBefore(com.pbms.common.utils.TimeProvider.now());
        if (isExpired && isVehicleInside(ticket.getPlateNumber())) {
            throw new IllegalArgumentException(
                    "The monthly ticket is expired, and the vehicle is currently inside the parking lot. Please exit the parking lot before renewing.");
        }
    }

    @Transactional
    /**
     * Tạo mới một vé tháng.
     * Logic mã giả:
     * 1. Gọi hàm validateCreateTicket để kiểm tra tính hợp lệ.
     * 2. Tìm xe trong DB, nếu chưa có thì tự động tạo mới xe.
     * 3. Lấy chính sách giá (PricingPolicy) và tính toán số tiền gốc dựa trên loại xe.
     * 4. Áp dụng các cấu hình giảm giá (ví dụ: mua 6 tháng giảm 10%, 12 tháng giảm 20%).
     * 5. Tạo thực thể MonthlyTicket, thiết lập thời gian bắt đầu và kết thúc.
     * 6. Lưu vé vào DB.
     * 7. Lưu giao dịch thanh toán (Transaction) để làm báo cáo doanh thu.
     * 8. Gửi email xác nhận đăng ký thành công cho khách.
     * 9. Trả về DTO của vé vừa tạo.
     */
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

    @Transactional
    public MonthlyTicketDTO updateTicketPlate(Long id, String newPlate) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monthly ticket not found"));

        if (newPlate == null || newPlate.isBlank()) {
            throw new IllegalArgumentException("New plate cannot be empty");
        }

        boolean hasBeenUsed = parkingSessionRepository.existsByPlateAndTimeInGreaterThanEqual(ticket.getPlateNumber(),
                ticket.getValidFrom());
        if (hasBeenUsed) {
            throw new IllegalStateException("This pass has already been used and cannot be modified");
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
                "<tr><th style='" + thTdStyles + "'>Biển số xe</th><td style='" + thTdStyles + "'>" + ticket.getPlateNumber() + "</td></tr>" +
                "<tr><th style='" + thTdStyles + "'>Loại phương tiện</th><td style='" + thTdStyles + "'>" + (ticket.getVehicleType() != null ? ticket.getVehicleType().getTypeName() : "N/A") + "</td></tr>" +
                "<tr><th style='" + thTdStyles + "'>Ngày bắt đầu</th><td style='" + thTdStyles + "'>" + startDateStr + "</td></tr>" +
                "<tr><th style='" + thTdStyles + "'>Ngày hết hạn</th><td style='" + thTdStyles + "'><strong>" + endDateStr + "</strong></td></tr>" +
                "</table>";

        if ("CREATE".equals(type)) {
            subject = "Xác nhận đăng ký Vé Tháng thành công - PBMS";
            htmlBody = "<div style='" + commonStyles + "'>" +
                    "<h2 style='color: #4CAF50;'>Đăng ký Vé Tháng Thành Công!</h2>" +
                    "<p>Xin chào " + user.getFullName() + ",</p>" +
                    "<p>Cảm ơn bạn đã đăng ký vé tháng tại hệ thống PBMS. Dưới đây là thông tin vé của bạn:</p>" +
                    detailsHtml +
                    "<p style='margin-top: 20px;'>Chúc bạn có những trải nghiệm tuyệt vời cùng chúng tôi!</p>" +
                    "</div>";
        } else if ("RENEW".equals(type)) {
            subject = "Xác nhận gia hạn Vé Tháng thành công - PBMS";
            htmlBody = "<div style='" + commonStyles + "'>" +
                    "<h2 style='color: #2196F3;'>Gia Hạn Vé Tháng Thành Công!</h2>" +
                    "<p>Xin chào " + user.getFullName() + ",</p>" +
                    "<p>Vé tháng của bạn đã được gia hạn thành công. Dưới đây là thông tin cập nhật:</p>" +
                    detailsHtml +
                    "<p style='margin-top: 20px;'>Cảm ơn bạn đã tiếp tục đồng hành cùng PBMS!</p>" +
                    "</div>";
        } else if ("EXPIRED".equals(type)) {
            subject = "Thông báo: Vé Tháng của bạn đã hết hạn - PBMS";
            htmlBody = "<div style='" + commonStyles + "'>" +
                    "<h2 style='color: #f44336;'>Vé Tháng Đã Hết Hạn</h2>" +
                    "<p>Xin chào " + user.getFullName() + ",</p>" +
                    "<p>Chúng tôi xin thông báo rằng vé tháng cho phương tiện của bạn đã hết hạn vào ngày <strong>" + endDateStr + "</strong>.</p>" +
                    detailsHtml +
                    "<p style='margin-top: 20px;'>Hệ thống đã tự động khóa quyền truy cập của phương tiện này dưới dạng vé tháng. Bạn vui lòng gia hạn vé tháng sớm nhất để không bị gián đoạn dịch vụ.</p>" +
                    "<p><a href='http://localhost:5173/customer/my-parking?tab=monthly' style='display: inline-block; padding: 10px 20px; background-color: #2196F3; color: white; text-decoration: none; border-radius: 5px;'>Gia hạn ngay</a></p>" +
                    "</div>";
        }

        emailService.sendHtmlEmail(user.getEmail(), subject, htmlBody);
    }

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
