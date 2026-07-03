/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Core business logic for the Pre-Booking (Reservation) feature.
 * @Dependencies:
 *  - ReservationRepository    (Local)
 *  - VehicleRepository        (Local)
 *  - VehicleTypeRepository    (Local)
 *  - ZoneRepository           (com.pbms.modules.infrastructure)
 *  - PricingCalculatorService (com.pbms.modules.finance)
 *  - ZoneRoutingService       (com.pbms.modules.operation)
 *  - SimpMessagingTemplate    (Spring WebSocket)
 *  - TaskScheduler            (Spring)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.finance.service.PricingCalculatorService;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.identity.repository.UserRepository;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.domain.Vehicle;
import com.pbms.modules.operation.domain.VehicleType;
import com.pbms.modules.operation.dto.CancelReservationRequest;
import com.pbms.modules.operation.dto.CreateReservationRequest;
import com.pbms.modules.operation.dto.ReservationDTO;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.VehicleRepository;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private PricingCalculatorService pricingCalculatorService;

    @Autowired
    private ZoneRoutingService zoneRoutingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    @Qualifier("taskScheduler")
    private TaskScheduler taskScheduler;

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    /**
     * @Function: getAllReservations
     * @Description: Returns reservations filtered by the caller's role.
     * @Logic_Steps:
     *  1. Lấy email từ SecurityContext.
     *  2. Nếu role là ROLE_CUSTOMER → chỉ trả reservation của xe thuộc user đó.
     *  3. Ngược lại (MANAGER, ADMIN, STAFF) → trả tất cả.
     */
    @Transactional(readOnly = true)
    public List<ReservationDTO> getAllReservations() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = auth != null ? auth.getName() : null;

        boolean isCustomer = auth != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))
                && auth.getAuthorities().stream().noneMatch(a ->
                a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_STAFF"));

        if (isCustomer && currentEmail != null) {
            String email = currentEmail;
            return reservationRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(r -> r.getVehicle() != null
                            && r.getVehicle().getUser() != null
                            && email.equals(r.getVehicle().getUser().getEmail()))
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }

        return reservationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // PREVIEW PRICE
    // -------------------------------------------------------------------------

    /**
     * @Function: previewPrice
     * @Description: Calculates estimated fee without saving to DB.
     * @Logic_Steps:
     *  1. Tính exitTime = entryTime + durationMinutes.
     *  2. Gọi PricingCalculatorService.calculateTotalFee().
     *  3. Return BigDecimal fee.
     */
    public BigDecimal previewPrice(Long vehicleTypeId, LocalDateTime expectedEntryTime, Integer durationMinutes) {
        LocalDateTime expectedExitTime = expectedEntryTime.plusMinutes(durationMinutes);
        return pricingCalculatorService.calculateTotalFee(vehicleTypeId, expectedEntryTime, expectedExitTime);
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    /**
     * @Function: createReservation
     * @Description: Creates a new pre-booking reservation for a customer.
     * @Logic_Steps:
     *  1. Lấy User hiện tại từ SecurityContext qua email.
     *  2. Tìm Vehicle theo plateNumber, nếu chưa có thì tạo mới gán user.
     *  3. Validate: xe không được có PENDING reservation khác.
     *  4. Tính phí qua previewPrice().
     *  5. Validate zone chưa đầy (occupancy < 100%).
     *  6. Build Reservation: status=PENDING, qrCode = "QR-" + 8 ký tự UUID.
     *  7. Save + scheduleReservationTasks() + return DTO.
     */
    @Transactional
    public ReservationDTO createReservation(CreateReservationRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        User currentUser = email != null ? userRepository.findByEmail(email).orElse(null) : null;

        Vehicle vehicle = vehicleRepository.findByPlateNumber(request.getPlateNumber())
                .orElseGet(() -> {
                    VehicleType type = vehicleTypeRepository.findById(request.getVehicleTypeId())
                            .orElseThrow(() -> new RuntimeException("VehicleType not found"));
                    return vehicleRepository.save(Vehicle.builder()
                            .vehicleType(type)
                            .plateNumber(request.getPlateNumber())
                            .user(currentUser)
                            .build());
                });

        if (vehicle.getUser() == null && currentUser != null) {
            vehicle.setUser(currentUser);
            vehicleRepository.save(vehicle);
        }

        List<Reservation> existingPending = reservationRepository
                .findByVehicle_PlateNumberAndStatus(request.getPlateNumber(), "PENDING");
        if (!existingPending.isEmpty()) {
            throw new IllegalStateException("Vehicle already has a pending reservation.");
        }

        BigDecimal fee = previewPrice(
                request.getVehicleTypeId(),
                request.getExpectedEntryTime(),
                request.getExpectedDurationMinutes());

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone not found"));

        BigDecimal occupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
        if (occupancy.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalStateException("Zone is full. Cannot make a reservation.");
        }

        Reservation reservation = Reservation.builder()
                .vehicle(vehicle)
                .zone(zone)
                .expectedEntryTime(request.getExpectedEntryTime())
                .expectedDurationMinutes(request.getExpectedDurationMinutes())
                .status("PENDING")
                .reservationFee(fee)
                .qrCode("QR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

        reservation = reservationRepository.save(reservation);
        scheduleReservationTasks(reservation);

        return mapToDTO(reservation);
    }

    // -------------------------------------------------------------------------
    // CANCEL
    // -------------------------------------------------------------------------

    /**
     * @Function: cancelReservation
     * @Description: Cancels a PENDING reservation with refund calculation.
     * @Logic_Steps:
     *  1. Tìm Reservation, validate status = PENDING.
     *  2. Tính diffMins = khoảng cách từ now đến expectedEntryTime.
     *  3. Xác định refundPercent:
     *     - diffMins > 30: hoàn 100%
     *     - 0 < diffMins <= 30: hoàn 50%
     *     - diffMins <= 0: hoàn 0% (đã quá giờ)
     *  4. Set status=CANCELLED, refundAmount, refundStatus="PENDING" nếu có hoàn tiền.
     *  5. Save và return DTO.
     */
    @Transactional
    public ReservationDTO cancelReservation(Long id, CancelReservationRequest cancelRequest) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!"PENDING".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only pending reservations can be cancelled");
        }

        LocalDateTime now = LocalDateTime.now();
        long diffMins = java.time.temporal.ChronoUnit.MINUTES.between(now, reservation.getExpectedEntryTime());

        BigDecimal refundPercent;
        if (diffMins > 30) {
            refundPercent = BigDecimal.ONE;
        } else if (diffMins > 0) {
            refundPercent = new BigDecimal("0.5");
        } else {
            refundPercent = BigDecimal.ZERO;
        }

        BigDecimal amountPaid = reservation.getReservationFee() != null
                ? reservation.getReservationFee() : BigDecimal.ZERO;
        BigDecimal refundAmount = amountPaid.multiply(refundPercent);

        reservation.setStatus("CANCELLED");
        reservation.setRefundAmount(refundAmount);
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            reservation.setRefundStatus("PENDING");
        }
        reservationRepository.save(reservation);

        return mapToDTO(reservation);
    }

    // -------------------------------------------------------------------------
    // UPDATE PLATE
    // -------------------------------------------------------------------------

    /**
     * @Function: updateReservationPlate
     * @Description: Updates vehicle plate on a PENDING reservation.
     * @Logic_Steps:
     *  1. Tìm Reservation, validate status = PENDING.
     *  2. Tìm hoặc tạo Vehicle với plate mới, giữ vehicleType của xe cũ.
     *  3. Gán vehicle mới vào reservation và save.
     */
    @Transactional
    public ReservationDTO updateReservationPlate(Long id, String newPlate) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!"PENDING".equals(reservation.getStatus())) {
            throw new IllegalStateException("Only pending reservations can be update");
        }

        Vehicle oldVehicle = reservation.getVehicle();

        Vehicle vehicle = vehicleRepository.findByPlateNumber(newPlate)
                .orElseGet(() -> vehicleRepository.save(Vehicle.builder()
                        .vehicleType(oldVehicle.getVehicleType())
                        .plateNumber(newPlate)
                        .build()));

        reservation.setVehicle(vehicle);
        reservationRepository.save(reservation);

        return mapToDTO(reservation);
    }

    // -------------------------------------------------------------------------
    // SCHEDULING HELPERS
    // -------------------------------------------------------------------------

    /**
     * @Function: scheduleReservationTasks
     * @Description: Schedules two one-time background tasks after reservation creation:
     *   Task 1 (notifyStaffTask)   - fires 30 mins before expectedEntryTime.
     *   Task 2 (expireReservationTask) - fires at expectedEntryTime + duration.
     */
    public void scheduleReservationTasks(Reservation res) {
        LocalDateTime notifyTime = res.getExpectedEntryTime().minusMinutes(30);
        LocalDateTime expireTime = res.getExpectedEntryTime().plusMinutes(res.getExpectedDurationMinutes());

        taskScheduler.schedule(
                () -> notifyStaffTask(res.getId()),
                java.util.Date.from(notifyTime.atZone(java.time.ZoneId.systemDefault()).toInstant())
        );

        taskScheduler.schedule(
                () -> expireReservationTask(res.getId()),
                java.util.Date.from(expireTime.atZone(java.time.ZoneId.systemDefault()).toInstant())
        );
    }

    @Transactional
    public void notifyStaffTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus()) || Boolean.TRUE.equals(res.getNotifiedEarlyArrival())) return;

        res.setNotifiedEarlyArrival(true);
        reservationRepository.save(res);

        // TODO: Remove null-check after Member1 implements WebSocketConfig
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/staff/notifications", (Object) Map.of(
                    "message", String.format("Vehicle %s is arriving soon at Zone %s.",
                            res.getVehicle().getPlateNumber(), res.getZone().getZoneName())
            ));
        } else {
            log.warn("WebSocket not configured yet. Skipping staff notification for reservation {}", reservationId);
        }
    }


    @Transactional
    public void expireReservationTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus())) return;

        res.setStatus("COMPLETED_UNUSED");
        reservationRepository.save(res);
        log.info("Reservation {} expired (no-show)", reservationId);
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPER
    // -------------------------------------------------------------------------

    private ReservationDTO mapToDTO(Reservation reservation) {
        return ReservationDTO.builder()
                .id(reservation.getId())
                .plateNumber(reservation.getVehicle().getPlateNumber())
                .vehicleType(reservation.getVehicle().getVehicleType().getTypeName())
                .zoneName(reservation.getZone() != null ? reservation.getZone().getZoneName() : "N/A")
                .slotName("N/A")
                .expectedEntryTime(reservation.getExpectedEntryTime())
                .expectedDurationMinutes(reservation.getExpectedDurationMinutes())
                .status(reservation.getStatus())
                .reservationFee(reservation.getReservationFee())
                .refundAmount(reservation.getRefundAmount())
                .refundStatus(reservation.getRefundStatus())
                .refundProofUrl(reservation.getRefundProofUrl())
                .refundRejectReason(reservation.getRefundRejectReason())
                .qrCode(reservation.getQrCode())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
