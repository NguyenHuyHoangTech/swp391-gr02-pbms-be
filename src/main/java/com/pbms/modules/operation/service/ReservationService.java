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

    public BigDecimal previewPrice(Long vehicleTypeId, LocalDateTime expectedEntryTime, Integer durationMinutes) {
        LocalDateTime expectedExitTime = expectedEntryTime.plusMinutes(durationMinutes);
        return pricingCalculatorService.calculateParkingFee(vehicleTypeId, expectedEntryTime, expectedExitTime);
    }

    /**
     * Hàm kiểm tra tính hợp lệ của dữ liệu trước khi tạo đặt chỗ mới.
     * Logic mã giả:
     * 1. Có biển số, có loại xe.
     * 2. Nếu xe đã có trong DB, không được đổi loại phương tiện, không nằm trong
     * Blacklist.
     * 3. Xe không được phép có đơn đặt chỗ nào khác đang ở trạng thái PENDING.
     * 4. Xe không được phép đang nằm trong bãi (ACTIVE session).
     * 5. Xe không được có vé tháng đang có hiệu lực.
     * 6. Khu vực đặt (Zone) không được phép quá tải.
     */
    public void validateCreateReservation(CreateReservationRequest request) {
        if (request.getVehicleTypeId() == null) {
            throw new IllegalArgumentException("Loại phương tiện không được để trống.");
        }
        if (request.getPlateNumber() == null || request.getPlateNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Biển số xe không được để trống.");
        }

        Vehicle vehicle = vehicleRepository.findByPlateNumber(request.getPlateNumber()).orElse(null);

        if (vehicle != null) {
            if (vehicle.getVehicleType() != null
                    && !vehicle.getVehicleType().getId().equals(request.getVehicleTypeId())) {
                throw new IllegalStateException(
                        "Biển số này đã được đăng ký với loại phương tiện khác trong hệ thống.");
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
            throw new IllegalStateException("Phương tiện này hiện đang ở trong bãi, không thể đặt chỗ.");
        }

        boolean hasActiveTicket = monthlyTicketRepository.findByPlateNumberAndStatus(request.getPlateNumber(), "ACTIVE")
                .isPresent();
        if (hasActiveTicket) {
            throw new IllegalStateException("Phương tiện này đang có vé tháng hợp lệ, không thể đặt chỗ trước.");
        }

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone not found"));

        BigDecimal occupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
        if (occupancy.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalStateException("Zone is full. Cannot make a reservation.");
        }
    }

    @Transactional
    /**
     * Tạo mới một đơn đặt chỗ (Reservation).
     * Logic mã giả:
     * 1. Validate đầu vào (biển số, loại xe, bãi đầy, xe đang trong bãi, nợ tiền).
     * 2. Tìm hoặc tạo mới xe (Vehicle) nếu chưa tồn tại.
     * 3. Tính toán giá tiền tự động (Dynamic Pricing) và thông báo qua Websocket.
     * 4. Tạo entity Reservation và lưu vào DB với trạng thái PENDING.
     * 5. Lưu giao dịch thanh toán (Gateway).
     * 6. Lập lịch các tác vụ chạy ngầm (Quartz Task): Cảnh báo gần đến giờ, Đánh
     * dấu trễ giờ, Thu phí No-show.
     */
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
        } else if (vehicle.getUser() != null && currentUser != null
                && !vehicle.getUser().getId().equals(currentUser.getId())) {
            vehicle.setUser(currentUser);
            vehicleRepository.save(vehicle);
            log.info("Overwritten ownership of vehicle {} to user {} via Reservation", request.getPlateNumber(),
                    currentUser.getEmail());
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

    @Transactional
    /**
     * Hủy đơn đặt chỗ.
     * Logic mã giả:
     * 1. Tìm đơn đặt chỗ. Nếu không phải trạng thái PENDING thì không thể hủy.
     * 2. Tính toán tiền hoàn trả dựa trên khoảng thời gian hủy sớm hay muộn so với
     * giờ dự kiến (dùng PolicyManager).
     * 3. Tính toán phí phạt (Penalty = Tiền đã đóng - Tiền hoàn trả).
     * 4. Tạo yêu cầu hoàn tiền (RefundRequest) và lưu vào hệ thống.
     * 5. Đổi trạng thái đơn đặt chỗ thành CANCELLED.
     * 6. Hủy tất cả các tác vụ chạy ngầm (Timer) đã hẹn giờ trước đó.
     */
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
        // Cung cap cho taskScheduler thoi diem can thuc thi (Instant tinh theo gio gia
        // lap).
        // taskScheduler se so sanh voi clock gia lap cua no de tinh toan delay tuong
        // ung.
        java.time.Instant targetInstant = targetTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        java.util.concurrent.ScheduledFuture<?> future = taskScheduler.schedule(task, targetInstant);

        taskRegistry.get(reservationId).put(type, new ScheduledTaskInfo(future, task, targetTime));
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Scheduling existing pending reservations...");
        List<Reservation> pendingReservations = reservationRepository.findByStatus("PENDING");
        for (Reservation res : pendingReservations) {
            scheduleReservationTasks(res);
        }
    }

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

    @Transactional
    public void lateWarningTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus()))
            return;

        log.info("Reservation {} is now late (reached expected entry time).", res.getId());
    }

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
                        java.time.Instant targetInstant = info.getTargetSimulatedTime()
                                .atZone(java.time.ZoneId.systemDefault()).toInstant();
                        java.util.concurrent.ScheduledFuture<?> newFuture = taskScheduler.schedule(info.getTask(),
                                targetInstant);
                        info.setFuture(newFuture);
                    }
                    rescheduledCount++;
                }
            }
        }
        log.info("Fast-forward sync complete: {} tasks executed instantly, {} tasks rescheduled", executedCount,
                rescheduledCount);
    }

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
            java.util.Optional<com.pbms.modules.finance.domain.RefundRequest> refundReq = refundRequestRepository
                    .findByReferenceTypeAndReferenceId("RESERVATION", String.valueOf(reservation.getId()));

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
