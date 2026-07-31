/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Lớp Service lõi (Core service) xử lý toàn bộ nghiệp vụ đặt chỗ (Reservation).
 *               Bao gồm: Validate điều kiện, tính toán giá tiền tự động, tạo mới/hủy đơn, 
 *               xử lý hoàn tiền, cập nhật biển số xe, và lên lịch tác vụ.
 * @Dependencies: 
 * - SystemConfigService, EmailService, PricingCalculatorService, ReservationPolicyManager, ZoneRoutingService
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.domain.Vehicle;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.dto.CreateReservationRequest;
import com.pbms.modules.operation.dto.ReservationDTO;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.VehicleRepository;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import com.pbms.modules.finance.service.PricingCalculatorService;
import com.pbms.modules.operation.dto.CancelReservationRequest;
import com.pbms.modules.finance.domain.RefundRequest;
import com.pbms.modules.finance.domain.Transaction;
import com.pbms.modules.finance.repository.RefundRequestRepository;
import com.pbms.modules.finance.repository.TransactionRepository;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneRoutingService zoneRoutingService;
    private final PricingCalculatorService pricingCalculatorService;
    private final RefundRequestRepository refundRequestRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ReservationPolicyManager reservationPolicyManager;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final org.springframework.scheduling.TaskScheduler taskScheduler;
    private final com.pbms.modules.operation.repository.ParkingSessionRepository parkingSessionRepository;
    private final com.pbms.modules.operation.repository.MonthlyTicketRepository monthlyTicketRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private ReservationService self;

    /**
     * Lấy danh sách tất cả các đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Lấy thông tin người dùng đang đăng nhập từ SecurityContext.
     * 2. Nếu người dùng là Khách hàng (Customer):
     *    - Tìm tất cả đơn đặt chỗ.
     *    - Lọc những đơn thuộc về biển số xe của khách hàng này.
     *    - Chuyển đổi sang định dạng DTO và trả về.
     * 3. Nếu người dùng là Quản lý/Nhân viên:
     *    - Lấy tất cả đơn đặt chỗ, chuyển đổi sang DTO và trả về.
     * </p>
     */
    @Transactional(readOnly = true)
    public List<ReservationDTO> getAllReservations() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = auth != null ? auth.getName() : null;
        boolean isCustomer = auth != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))
                && auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));

        if (isCustomer && currentEmail != null) {
            return reservationRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(r -> r.getVehicle() != null && r.getVehicle().getUser() != null
                            && currentEmail.equals(r.getVehicle().getUser().getEmail()))
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }

        return reservationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Xem trước giá tiền cho đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Tính toán thời gian dự kiến ra (Thời gian vào + Thời lượng đỗ).
     * 2. Gọi PricingCalculatorService để tính toán phí dựa trên loại xe, giờ vào và giờ ra.
     * 3. Trả về mức giá tính được.
     * </p>
     */
    public BigDecimal previewPrice(Long vehicleTypeId, LocalDateTime expectedEntryTime, Integer durationMinutes) {
        LocalDateTime expectedExitTime = expectedEntryTime.plusMinutes(durationMinutes);
        return pricingCalculatorService.calculateParkingFee(vehicleTypeId, expectedEntryTime, expectedExitTime);
    }

    /**
     * Xác thực các điều kiện trước khi tạo đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Kiểm tra đầu vào: Loại xe và biển số xe không được để trống.
     * 2. Tìm xe dựa vào biển số:
     *    - Nếu xe tồn tại và khác loại xe yêu cầu -> báo lỗi.
     *    - Nếu xe bị đưa vào Blacklist -> báo lỗi.
     * 3. Kiểm tra xem xe có đang có đơn đặt chỗ nào ở trạng thái PENDING không -> Nếu có, báo lỗi.
     * 4. Kiểm tra xe có đang ở trong bãi không -> Nếu có, báo lỗi.
     * 5. Kiểm tra xe có vé tháng hợp lệ không -> Nếu có, báo lỗi.
     * 6. Kiểm tra khu vực đỗ xe (Zone):
     *    - Tính toán sức chứa hiện tại của khu vực.
     *    - Nếu khu vực đã đầy (>= 100%) -> báo lỗi không thể đặt.
     * </p>
     */
    public void validateCreateReservation(CreateReservationRequest request) {
        if (request.getVehicleTypeId() == null) {
            throw new IllegalArgumentException("Vehicle type cannot be empty.");
        }
        if (request.getPlateNumber() == null || request.getPlateNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be empty.");
        }

        Vehicle vehicle = vehicleRepository.findByPlateNumber(request.getPlateNumber()).orElse(null);

        if (vehicle != null) {
            if (vehicle.getVehicleType() != null
                    && !vehicle.getVehicleType().getId().equals(request.getVehicleTypeId())) {
                throw new IllegalStateException(
                        "This license plate is already registered with another vehicle type in the system.");
            }
            if (Boolean.TRUE.equals(vehicle.getIsBlacklisted())) {
                throw new IllegalStateException("Cannot make a reservation because the vehicle is in the Blacklist.");
            }
        }

        List<Reservation> existing = reservationRepository.findByVehicle_PlateNumberAndStatus(request.getPlateNumber(),
                "PENDING");
        if (!existing.isEmpty()) {
            throw new IllegalStateException("Vehicle already has a pending reservation.");
        }

        List<com.pbms.modules.operation.domain.ParkingSession> activeSessions = parkingSessionRepository
                .findByPlateAndStatus(request.getPlateNumber(), "ACTIVE").stream().toList();
        if (!activeSessions.isEmpty()) {
            throw new IllegalStateException("This vehicle is currently inside the parking lot, cannot make a reservation.");
        }

        boolean hasActiveTicket = monthlyTicketRepository.findByPlateNumberAndStatus(request.getPlateNumber(), "ACTIVE").isPresent();
        if (hasActiveTicket) {
            throw new IllegalStateException("This vehicle has a valid monthly ticket, cannot make a reservation.");
        }

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone not found"));

        BigDecimal occupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
        if (occupancy.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalStateException("Zone is full. Cannot make a reservation.");
        }
    }

    /**
     * Tạo mới một đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Gọi hàm validateCreateReservation() để kiểm tra tính hợp lệ.
     * 2. Lấy thông tin người dùng hiện tại (nếu có).
     * 3. Tìm hoặc Tạo mới thông tin Xe (Vehicle) theo biển số. Gắn user hiện tại cho xe nếu cần.
     * 4. Tính toán phí đặt chỗ bằng cách gọi previewPrice().
     * 5. Lấy thông tin Khu vực đỗ xe (Zone).
     * 6. Tạo đối tượng Reservation với trạng thái PENDING.
     * 7. Lưu vào cơ sở dữ liệu.
     * 8. Gọi hàm scheduleReservationTasks() để lên lịch các tác vụ hẹn giờ (cảnh báo, hết hạn).
     * 9. Trả về thông tin đơn đặt chỗ dạng DTO.
     * </p>
     */
    @Transactional
    public ReservationDTO createReservation(CreateReservationRequest request) {
        validateCreateReservation(request);

        String email = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        User currentUser = email != null ? userRepository.findByEmail(email).orElse(null) : null;

        // 1. Get or Create Vehicle
        Vehicle vehicle = vehicleRepository.findByPlateNumber(request.getPlateNumber())
                .orElseGet(() -> {
                    VehicleType type = vehicleTypeRepository.findById(request.getVehicleTypeId())
                            .orElseThrow(() -> new RuntimeException("An error occurred"));
                    Vehicle newVehicle = Vehicle.builder()
                            .vehicleType(type)
                            .plateNumber(request.getPlateNumber())
                            .user(currentUser)
                            .build();
                    return vehicleRepository.save(newVehicle);
                });

        if (vehicle.getUser() == null && currentUser != null) {
            vehicle.setUser(currentUser);
            vehicleRepository.save(vehicle);
        } else if (vehicle.getUser() != null && currentUser != null && !vehicle.getUser().getId().equals(currentUser.getId())) {
            vehicle.setUser(currentUser);
            vehicleRepository.save(vehicle);
            log.info("Overwritten ownership of vehicle {} to user {} via Reservation", request.getPlateNumber(), currentUser.getEmail());
        }

        // 2. Calculate Price dynamically
        BigDecimal fee = previewPrice(request.getVehicleTypeId(), request.getExpectedEntryTime(),
                request.getExpectedDurationMinutes());

        // 3. Find Zone
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone not found"));

        // 4. Create Reservation
        Reservation reservation = Reservation.builder()
                .vehicle(vehicle)
                .zone(zone)
                .expectedEntryTime(request.getExpectedEntryTime())
                .expectedDurationMinutes(request.getExpectedDurationMinutes())
                .status("PENDING") // PENDING means paid but hasn't entered
                .reservationFee(fee)
                .build();

        if (reservation.getCreatedAt() == null) {
            reservation.setCreatedAt(com.pbms.common.utils.TimeProvider.now());
        }

        reservation = reservationRepository.save(reservation);

        scheduleReservationTasks(reservation);

        return mapToDTO(reservation);
    }

    /**
     * Cập nhật biển số xe cho đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Tìm đơn đặt chỗ theo ID.
     * 2. Kiểm tra trạng thái phải là PENDING và chưa quá thời gian hết hạn dự kiến.
     * 3. Kiểm tra biển số mới không được để trống.
     * 4. Tìm xe theo biển số mới, nếu chưa có thì tạo mới và kế thừa thông tin người dùng từ xe cũ.
     * 5. Gắn xe mới vào đơn đặt chỗ và lưu lại DB.
     * 6. Trả về thông tin DTO.
     * </p>
     */
    @Transactional
    public ReservationDTO updateReservationPlate(Long id, String newPlate) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!"PENDING".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only pending reservations can be modified");
        }

        LocalDateTime expectedExitTime = reservation.getExpectedEntryTime()
                .plusMinutes(reservation.getExpectedDurationMinutes());
        if (com.pbms.common.utils.TimeProvider.now().isAfter(expectedExitTime)) {
            throw new IllegalStateException("Reservation has expired and cannot be modified");
        }

        if (newPlate == null || newPlate.isBlank()) {
            throw new IllegalArgumentException("New plate cannot be empty");
        }

        Vehicle oldVehicle = reservation.getVehicle();

        Vehicle vehicle = vehicleRepository.findByPlateNumber(newPlate)
                .orElseGet(() -> {
                    Vehicle newVehicle = Vehicle.builder()
                            .vehicleType(oldVehicle.getVehicleType())
                            .plateNumber(newPlate)
                            .user(oldVehicle.getUser())
                            .build();
                    return vehicleRepository.save(newVehicle);
                });

        if (vehicle.getUser() == null && oldVehicle.getUser() != null) {
            vehicle.setUser(oldVehicle.getUser());
            vehicle = vehicleRepository.save(vehicle);
        }

        reservation.setVehicle(vehicle);
        reservationRepository.save(reservation);

        return mapToDTO(reservation);
    }

    /**
     * Hủy đơn đặt chỗ và xử lý hoàn tiền/phạt.
     * <p>
     * Mã giả:
     * 1. Tìm đơn đặt chỗ theo ID, kiểm tra phải ở trạng thái PENDING.
     * 2. Tính toán khoảng thời gian từ hiện tại đến giờ vào dự kiến.
     * 3. Dựa trên chính sách (sớm/muộn), xác định phần trăm hoàn tiền (ví dụ: hủy sớm hoàn 100%, hủy muộn hoàn 50%).
     * 4. Tính số tiền được hoàn (refundAmount) và số tiền bị phạt (penaltyFee).
     * 5. Cập nhật trạng thái đơn thành CANCELLED.
     * 6. Nếu có tiền hoàn -> Tạo yêu cầu hoàn tiền (RefundRequest) chờ xử lý.
     * 7. Nếu có tiền phạt -> Ghi nhận thành Doanh thu hệ thống (Transaction).
     * 8. Trả về DTO của đơn đã hủy.
     * </p>
     */
    @Transactional
    public ReservationDTO cancelReservation(Long id, CancelReservationRequest cancelRequest) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!"PENDING".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only pending reservations can be cancelled");
        }

        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        LocalDateTime entryTime = reservation.getExpectedEntryTime();
        long diffMins = java.time.temporal.ChronoUnit.MINUTES.between(now, entryTime);

        int windowMinutes = reservationPolicyManager.getEarlyWindowMins();

        BigDecimal refundPercent = BigDecimal.ZERO;
        if (diffMins > windowMinutes) {
            refundPercent = reservationPolicyManager.getRefundEarlyPercent();
        } else if (diffMins > 0 && diffMins <= windowMinutes) {
            refundPercent = reservationPolicyManager.getRefundLatePercent();
        }

        BigDecimal amountPaid = reservation.getReservationFee() != null ? reservation.getReservationFee()
                : BigDecimal.ZERO;
        BigDecimal refundAmount = amountPaid.multiply(refundPercent);
        BigDecimal penaltyFee = amountPaid.subtract(refundAmount);

        reservation.setStatus("CANCELLED");
        reservationRepository.save(reservation);

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            User user = reservation.getVehicle() != null ? reservation.getVehicle().getUser() : null;
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            if (user == null) {
                user = userRepository.findByEmail(email).orElse(null);
            }

            if (user != null) {
                RefundRequest refund = RefundRequest.builder()
                        .user(user)
                        .referenceType("RESERVATION")
                        .referenceId(String.valueOf(reservation.getId()))
                        .paidAmount(amountPaid)
                        .penaltyFee(penaltyFee)
                        .refundAmount(refundAmount)
                        .bankName(cancelRequest.getBankName())
                        .accountNumber(cancelRequest.getAccountNumber())
                        .accountName(cancelRequest.getAccountName())
                        .status("PENDING")
                        .build();
                refundRequestRepository.save(refund);
            } else {
                log.error("Cannot find User address (email: {}) RefundRequest", email);
            }
        }

        // Save Penalty as Revenue Transaction
        if (penaltyFee.compareTo(BigDecimal.ZERO) > 0) {
            Transaction penaltyTx = Transaction.builder()
                    .amount(penaltyFee)
                    .paymentMethod("GATEWAY") // Default for cancellation penalty
                    .status("SUCCESS")
                    .transactionReference("PENALTY-RES-" + reservation.getId())
                    .build();
            penaltyTx.setCreatedAt(now); // Set the simulated time manually
            penaltyTx = transactionRepository.save(penaltyTx);
            transactionRepository.updateCreatedAtNative(penaltyTx.getId(), now);
        }

        return mapToDTO(reservation);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ScheduledTaskInfo {
        private java.util.concurrent.ScheduledFuture<?> future;
        private Runnable task;
        private LocalDateTime targetSimulatedTime;
    }

    private final java.util.Map<Long, java.util.Map<String, ScheduledTaskInfo>> taskRegistry = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Hủy tất cả các tác vụ hẹn giờ liên quan đến một đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Lấy danh sách các tác vụ của đơn đặt chỗ từ taskRegistry.
     * 2. Hủy (cancel) từng tác vụ trong ScheduledFuture.
     * 3. Xóa thông tin đơn đặt chỗ khỏi taskRegistry.
     * </p>
     */
    private void cancelAllTasks(Long reservationId) {
        java.util.Map<String, ScheduledTaskInfo> tasks = taskRegistry.get(reservationId);
        if (tasks != null) {
            tasks.values().forEach(info -> {
                if (info.getFuture() != null)
                    info.getFuture().cancel(false);
            });
            taskRegistry.remove(reservationId);
        }
    }

    /**
     * Lấy danh sách các bộ đếm thời gian (timers) phục vụ mục đích debug.
     * <p>
     * Mã giả:
     * 1. Duyệt qua taskRegistry.
     * 2. Lấy thông tin liên quan của mỗi timer: reservationId, loại tác vụ, thời gian chạy dự kiến.
     * 3. Kiểm tra xem timer đã được kích hoạt hay chưa.
     * 4. Trả về danh sách kết quả.
     * </p>
     */
    public java.util.List<java.util.Map<String, Object>> getDebugTimers() {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        for (java.util.Map.Entry<Long, java.util.Map<String, ScheduledTaskInfo>> entry : taskRegistry.entrySet()) {
            Long resId = entry.getKey();
            Reservation res = reservationRepository.findById(resId).orElse(null);
            String plateNumber = res != null && res.getVehicle() != null ? res.getVehicle().getPlateNumber() : "";
            Long vehicleTypeId = res != null && res.getVehicle() != null && res.getVehicle().getVehicleType() != null
                    ? res.getVehicle().getVehicleType().getId()
                    : null;

            for (java.util.Map.Entry<String, ScheduledTaskInfo> taskEntry : entry.getValue().entrySet()) {
                java.util.Map<String, Object> info = new java.util.HashMap<>();
                info.put("reservationId", resId);
                info.put("plateNumber", plateNumber);
                info.put("vehicleTypeId", vehicleTypeId);
                info.put("taskType", taskEntry.getKey());
                info.put("targetTime", taskEntry.getValue().getTargetSimulatedTime().toString());
                info.put("isTriggered", !now.isBefore(taskEntry.getValue().getTargetSimulatedTime()));
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Đăng ký một tác vụ hẹn giờ vào hệ thống.
     * <p>
     * Mã giả:
     * 1. Kiểm tra thời gian mục tiêu (targetTime) so với thời gian hiện tại.
     * 2. Nếu đã qua thời gian mục tiêu -> Chạy tác vụ ngay lập tức.
     * 3. Nếu chưa đến -> Sử dụng TaskScheduler để lên lịch chạy tác vụ tại thời điểm targetTime.
     * 4. Lưu thông tin vào taskRegistry để quản lý.
     * </p>
     */
    private void registerTask(Long reservationId, String type, LocalDateTime targetTime, Runnable task) {
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        taskRegistry.computeIfAbsent(reservationId, k -> new java.util.concurrent.ConcurrentHashMap<>());

        if (!now.isBefore(targetTime)) {
            // execute immediately if time has passed
            taskRegistry.get(reservationId).put(type, new ScheduledTaskInfo(null, task, targetTime));
            task.run();
            return;
        }

        // DAY CHINH LA LUC KHOI TAO BO DEM:
        // Cung cap cho taskScheduler thoi diem can thuc thi (Instant tinh theo gio gia lap).
        // taskScheduler se so sanh voi clock gia lap cua no de tinh toan delay tuong ung.
        java.time.Instant targetInstant = targetTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        java.util.concurrent.ScheduledFuture<?> future = taskScheduler.schedule(task, targetInstant);

        taskRegistry.get(reservationId).put(type, new ScheduledTaskInfo(future, task, targetTime));
    }

    /**
     * Hàm chạy khi ứng dụng khởi động thành công.
     * <p>
     * Mã giả:
     * 1. Tìm tất cả các đơn đặt chỗ đang có trạng thái PENDING.
     * 2. Khởi tạo lại các tác vụ hẹn giờ (scheduleReservationTasks) cho từng đơn (tránh mất timer khi server restart).
     * </p>
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Scheduling existing pending reservations...");
        List<Reservation> pendingReservations = reservationRepository.findByStatus("PENDING");
        for (Reservation res : pendingReservations) {
            scheduleReservationTasks(res);
        }
    }

    /**
     * Lên lịch các bộ đếm thời gian cho một đơn đặt chỗ.
     * <p>
     * Mã giả:
     * 1. Hủy các tác vụ cũ của đơn (nếu có).
     * 2. Tính toán 3 mốc thời gian: Thời điểm thông báo sớm (notifyTime), Giờ vào dự kiến (entryTime), Giờ hết hạn (expireTime).
     * 3. Đăng ký tác vụ 1: Thông báo cho staff (NOTIFY) tại notifyTime.
     * 4. Đăng ký tác vụ 2: Đánh dấu trễ giờ (ENTRY) tại entryTime.
     * 5. Đăng ký tác vụ 3: Đánh dấu kết thúc đơn (EXPIRE) tại expireTime.
     * </p>
     */
    public void scheduleReservationTasks(Reservation res) {
        cancelAllTasks(res.getId());

        int windowMinutes = reservationPolicyManager.getEarlyWindowMins();

        LocalDateTime notifyTime = res.getExpectedEntryTime().minusMinutes(windowMinutes);
        LocalDateTime entryTime = res.getExpectedEntryTime();
        int duration = res.getExpectedDurationMinutes() != null ? res.getExpectedDurationMinutes()
                : reservationPolicyManager.getDefaultDurationMins();
        LocalDateTime expireTime = res.getExpectedEntryTime().plusMinutes(duration);

        // Timer 1: Notification & 50% penalty activation
        // BỘ ĐẾM 1: Đếm đến trước giờ đặt chỗ (trừ đi số phút quy định)
        // Mục đích: Cảnh báo nhân viên xe sắp đến, kiểm tra xem bãi có đang đầy không
        // để giải quyết xung đột sớm.
        if (res.getNotifiedEarlyArrival() == null || !res.getNotifiedEarlyArrival()) {
            registerTask(res.getId(), "NOTIFY", notifyTime, () -> self.notifyStaffTask(res.getId()));
        } else {
            taskRegistry.computeIfAbsent(res.getId(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put("NOTIFY", new ScheduledTaskInfo(null, null, notifyTime));
        }

        // Timer 2: Expected Entry (Late Warning / 100% penalty)
        // BỘ ĐẾM 2: Đếm đến đúng giờ đặt chỗ dự kiến
        // Mục đích: Cảnh báo khách bắt đầu đến muộn, làm mốc tính phạt hoặc hủy vé.
        registerTask(res.getId(), "ENTRY", entryTime, () -> self.lateWarningTask(res.getId()));

        // Timer 3: End of Booking
        // BỘ ĐẾM 3: Đếm đến khi hết giờ đỗ xe (Thời gian vào + Thời gian đỗ)
        // Mục đích: Nếu xe không đến (No-show) -> Hủy & phạt 100%. Nếu xe chưa ra ->
        // Tính thêm phí vãng lai.
        registerTask(res.getId(), "EXPIRE", expireTime, () -> self.endOfBookingTask(res.getId()));
    }

    /**
     * Tác vụ: Thông báo cho nhân viên về xe sắp tới.
     * <p>
     * Mã giả:
     * 1. Lấy đơn đặt chỗ. Trừ khi đang ở trạng thái PENDING và chưa thông báo thì mới xử lý.
     * 2. Đánh dấu đơn là đã thông báo sớm (NotifiedEarlyArrival = true).
     * 3. Kiểm tra xem khu vực đỗ xe dự kiến có bị ĐẦY vật lý hay không.
     * 4. Nếu ĐẦY -> Gửi cảnh báo XUNG ĐỘT cho nhân viên qua Websocket.
     * 5. Nếu chưa ĐẦY -> Gửi thông báo NHẮC NHỞ có xe sắp đến.
     * </p>
     */
    @Transactional
    public void notifyStaffTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus()) || Boolean.TRUE.equals(res.getNotifiedEarlyArrival()))
            return;

        res.setNotifiedEarlyArrival(true);
        reservationRepository.save(res);

        if (res.getZone() != null && res.getZone().getFloor() != null) {

            if (zoneRoutingService.isZonePhysicallyFull(res.getZone().getId())) {
                String message = String.format(
                        "Zone %s is FULL but vehicle %s is arriving soon. Please resolve this conflict.",
                        res.getZone().getZoneName(), res.getVehicle().getPlateNumber());
                Object payload = java.util.Map.of(
                        "type", "ZONE_CONFLICT",
                        "reservationId", res.getId(),
                        "plate", res.getVehicle().getPlateNumber(),
                        "customer",
                        res.getVehicle() != null && res.getVehicle().getUser() != null
                                ? res.getVehicle().getUser().getFullName()
                                : "Guest",
                        "zoneName", res.getZone().getZoneName(),
                        "vehicleTypeId", res.getVehicle().getVehicleType().getId(),
                        "message", message);
                messagingTemplate.convertAndSend("/topic/staff/notifications", payload);
            } else {
                String message = String.format("Vehicle %s is arriving soon for reservation at Zone %s.",
                        res.getVehicle().getPlateNumber(), res.getZone().getZoneName());
                Object payload = java.util.Map.of(
                        "type", "ZONE_RESERVED",
                        "message", message,
                        "plate", res.getVehicle().getPlateNumber(),
                        "zoneName", res.getZone().getZoneName());
                messagingTemplate.convertAndSend("/topic/staff/notifications", payload);
            }
        }
    }

    /**
     * Tác vụ: Đánh dấu khách đến muộn.
     * <p>
     * Mã giả:
     * 1. Tìm đơn đặt chỗ, nếu không phải PENDING thì bỏ qua.
     * 2. Ghi log cảnh báo đơn đã bắt đầu bị trễ giờ (đến giờ hẹn mà chưa check-in).
     * </p>
     */
    @Transactional
    public void lateWarningTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus()))
            return;

        log.info("Reservation {} is now late (reached expected entry time).", res.getId());
    }

    /**
     * Tác vụ: Kết thúc đơn đặt chỗ (Hết thời gian đỗ).
     * <p>
     * Mã giả:
     * 1. Tìm đơn đặt chỗ. Lấy phiên đỗ xe (ParkingSession) mới nhất tương ứng với đơn.
     * 2. Nếu xe đang ở trong bãi (ACTIVE) -> Xe chưa ra, cập nhật trạng thái đơn thành COMPLETED, bắt đầu tính thêm phí vãng lai sau này.
     * 3. Nếu xe đã ra (COMPLETED session) -> Cập nhật trạng thái đơn thành COMPLETED.
     * 4. Nếu không có phiên đỗ xe nào (No-show, xe không đến):
     *    - Cập nhật trạng thái thành COMPLETED_UNUSED.
     *    - Thu phí phạt 100% (saveNoShowPenalty).
     *    - Bắn thông báo Websocket cho nhân viên báo đơn đã hết hạn.
     * </p>
     */
    @Transactional
    public void endOfBookingTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null)
            return;

        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        java.util.Optional<com.pbms.modules.operation.domain.ParkingSession> psOpt = parkingSessionRepository
                .findTopByReservationIdOrderByTimeInDesc(reservationId);

        if (psOpt.isPresent()) {
            com.pbms.modules.operation.domain.ParkingSession ps = psOpt.get();
            if ("ACTIVE".equals(ps.getStatus())) {
                if (!"COMPLETED".equals(res.getStatus())) {
                    log.info("Reservation {} completed. Car still in lot. Switching to guest pricing.", res.getId());
                    res.setStatus("COMPLETED");
                    reservationRepository.save(res);
                }
            } else if ("COMPLETED".equals(ps.getStatus())) {
                if (!"COMPLETED".equals(res.getStatus())) {
                    log.info("Reservation {} completed normally.", res.getId());
                    res.setStatus("COMPLETED");
                    reservationRepository.save(res);
                }
            }
        } else {
            if ("PENDING".equals(res.getStatus())) {
                log.info("Reservation {} marked as COMPLETED_UNUSED (No-show)", res.getId());
                res.setStatus("COMPLETED_UNUSED");
                reservationRepository.save(res);
                saveNoShowPenalty(res, now);

                String resPlate = res.getVehicle() != null ? res.getVehicle().getPlateNumber() : "N/A";
                String resZone = res.getZone() != null ? res.getZone().getZoneName() : "N/A";
                messagingTemplate.convertAndSend("/topic/staff/notifications",
                        String.format(
                                "{\"type\":\"ZONE_RESERVED\", \"reservationId\":%d, \"plate\":\"%s\", \"zoneName\":\"%s\", \"message\":\"Reservation expired.\"}",
                                res.getId(), resPlate, resZone));
            }
        }
    }

    /**
     * Xử lý lưu giao dịch phạt khi khách hàng không đến (No-show).
     * <p>
     * Mã giả:
     * 1. Lấy số tiền khách đã thanh toán.
     * 2. Nếu số tiền > 0, tạo một giao dịch mới (Transaction) ghi nhận khoản này là tiền phạt hệ thống thu được.
     * 3. Lưu vào DB với thời gian xảy ra giao dịch là thời gian hiện tại (hoặc thời gian mô phỏng).
     * </p>
     */
    private void saveNoShowPenalty(Reservation reservation, LocalDateTime now) {
        BigDecimal penaltyFee = reservation.getReservationFee() != null ? reservation.getReservationFee()
                : BigDecimal.ZERO;
        if (penaltyFee.compareTo(BigDecimal.ZERO) > 0) {
            Transaction penaltyTx = Transaction.builder()
                    .amount(penaltyFee)
                    .paymentMethod("GATEWAY") // Default for cancellation penalty
                    .status("SUCCESS")
                    .transactionReference("PENALTY-RES-" + reservation.getId())
                    .build();
            if (now != null) {
                penaltyTx.setCreatedAt(now);
            } else {
                penaltyTx.setCreatedAt(com.pbms.common.utils.TimeProvider.now());
            }
            penaltyTx = transactionRepository.save(penaltyTx);
            if (now != null) {
                transactionRepository.updateCreatedAtNative(penaltyTx.getId(), now);
            }
        }
    }

    /**
     * Xử lý sự kiện Tua Nhanh Thời Gian (Time Fast-Forward).
     * <p>
     * Mã giả:
     * 1. Nhận sự kiện có thời gian mô phỏng mới.
     * 2. Duyệt qua tất cả các tác vụ đang được hẹn giờ.
     * 3. Hủy bỏ hẹn giờ hiện tại.
     * 4. So sánh thời gian chạy dự kiến với thời gian mô phỏng mới:
     *    - Nếu thời gian mô phỏng mới đã vượt qua giờ chạy -> Thực thi tác vụ ngay lập tức.
     *    - Nếu chưa vượt qua -> Lên lịch lại (reschedule) tác vụ tương ứng với thời gian còn lại.
     * </p>
     */
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime now = event.getNewSimulatedTime();
        log.info("Handling TimeFastForwardedEvent in ReservationService. Syncing {} tasks for simulated time: {}",
                taskRegistry.size(), now);

        int executedCount = 0;
        int rescheduledCount = 0;

        for (java.util.Map.Entry<Long, java.util.Map<String, ScheduledTaskInfo>> entry : taskRegistry.entrySet()) {
            java.util.Map<String, ScheduledTaskInfo> tasks = entry.getValue();

            for (java.util.Map.Entry<String, ScheduledTaskInfo> taskEntry : tasks.entrySet()) {
                ScheduledTaskInfo info = taskEntry.getValue();
                if (info.getFuture() != null)
                    info.getFuture().cancel(false);

                if (!now.isBefore(info.getTargetSimulatedTime())) {
                    // Time has passed, execute now synchronously
                    if (info.getTask() != null) {
                        info.getTask().run();
                    }
                    executedCount++;
                } else {
                    // Reschedule for remaining time
                    if (info.getTask() != null) {
                        java.time.Instant targetInstant = info.getTargetSimulatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant();
                        java.util.concurrent.ScheduledFuture<?> newFuture = taskScheduler.schedule(info.getTask(), targetInstant);
                        info.setFuture(newFuture);
                    }
                    rescheduledCount++;
                }
            }
        }
        log.info("Fast-forward sync complete: {} tasks executed instantly, {} tasks rescheduled", executedCount,
                rescheduledCount);
    }

    /**
     * Chuyển đổi đối tượng Reservation thành ReservationDTO.
     * <p>
     * Mã giả:
     * 1. Tìm phiên đỗ xe (ParkingSession) hoặc yêu cầu hoàn tiền (RefundRequest) liên kết với đơn.
     * 2. Nếu đơn bị HỦY (CANCELLED) -> Đọc số tiền hoàn, trạng thái hoàn tiền và tính số tiền bị phạt.
     * 3. Nếu đơn Không sử dụng (COMPLETED_UNUSED) -> Tiền phạt bằng 100% phí đã thu.
     * 4. Build và trả về đối tượng ReservationDTO chứa đầy đủ thông tin.
     * </p>
     */
    private ReservationDTO mapToDTO(Reservation reservation) {
        String actualIn = null;
        String actualOut = null;
        BigDecimal penaltyFee = null;
        String rfid = null;
        String userEmail = reservation.getVehicle() != null && reservation.getVehicle().getUser() != null
                ? reservation.getVehicle().getUser().getEmail()
                : "N/A";

        java.util.Optional<com.pbms.modules.operation.domain.ParkingSession> psOpt = parkingSessionRepository
                .findTopByReservationIdOrderByTimeInDesc(reservation.getId());
        String refundStatus = null;
        BigDecimal refundAmount = BigDecimal.ZERO;
        Long refundRequestId = null;
        String rejectReason = null;
        String refundProofUrl = null;

        if (psOpt.isPresent()) {
            com.pbms.modules.operation.domain.ParkingSession ps = psOpt.get();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("HH:mm dd/MM/yyyy");
            actualIn = ps.getTimeIn() != null ? ps.getTimeIn().format(formatter) : null;
            actualOut = ps.getTimeOut() != null ? ps.getTimeOut().format(formatter) : null;
            penaltyFee = ps.getPenaltyFee();
            rfid = ps.getRfidCard() != null ? ps.getRfidCard().getCardCode() : null;
        } else if ("CANCELLED".equals(reservation.getStatus())) {
            BigDecimal resFee = reservation.getReservationFee() != null ? reservation.getReservationFee()
                    : BigDecimal.ZERO;

            // Look up RefundRequest instead of using redundant columns
            java.util.Optional<com.pbms.modules.finance.domain.RefundRequest> refundReq =
                    refundRequestRepository.findByReferenceTypeAndReferenceId("RESERVATION", String.valueOf(reservation.getId()));

            if (refundReq.isPresent()) {
                com.pbms.modules.finance.domain.RefundRequest req = refundReq.get();
                refundAmount = req.getRefundAmount() != null ? req.getRefundAmount() : BigDecimal.ZERO;
                refundStatus = req.getStatus();
                refundRequestId = req.getId();
                rejectReason = req.getRejectReason();
                refundProofUrl = req.getProofUrl();
            }
            penaltyFee = resFee.subtract(refundAmount);
        } else if ("COMPLETED_UNUSED".equals(reservation.getStatus())) {
            penaltyFee = reservation.getReservationFee() != null ? reservation.getReservationFee() : BigDecimal.ZERO;
        }

        return ReservationDTO.builder()
                .id(reservation.getId())
                .plateNumber(reservation.getVehicle().getPlateNumber())
                .vehicleType(reservation.getVehicle().getVehicleType().getTypeName())
                .vehicleTypeId(reservation.getVehicle().getVehicleType().getId())
                .rfid(rfid)
                .zoneName(reservation.getZone() != null ? reservation.getZone().getZoneName() : "N/A")
                .slotName("N/A") // Slots are assigned dynamically by IoT
                .expectedEntryTime(reservation.getExpectedEntryTime())
                .expectedDurationMinutes(reservation.getExpectedDurationMinutes())
                .status(reservation.getStatus())
                .reservationFee(reservation.getReservationFee())
                .actualIn(actualIn)
                .actualOut(actualOut)
                .penaltyFee(penaltyFee)
                .userEmail(userEmail)
                .createdAt(reservation.getCreatedAt())
                .refundStatus(refundStatus)
                .refundAmount(refundAmount)
                .refundRequestId(refundRequestId)
                .rejectReason(rejectReason)
                .refundProofUrl(refundProofUrl)
                .build();
    }
}
