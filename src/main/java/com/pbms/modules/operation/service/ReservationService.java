package com.pbms.modules.operation.service;

import com.pbms.modules.finance.repository.PricingPolicyRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ZoneRepository zoneRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final ZoneRoutingService zoneRoutingService;
    private final PricingCalculatorService pricingCalculatorService;
    private final RefundRequestRepository refundRequestRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final org.springframework.scheduling.TaskScheduler taskScheduler;

    @Transactional(readOnly = true)
    public List<ReservationDTO> getAllReservations() {
        return reservationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public BigDecimal previewPrice(Long vehicleTypeId, LocalDateTime expectedEntryTime, Integer durationMinutes) {
        LocalDateTime expectedExitTime = expectedEntryTime.plusMinutes(durationMinutes);
        return pricingCalculatorService.calculateTotalFee(vehicleTypeId, expectedEntryTime, expectedExitTime);
    }

    @Transactional
    public ReservationDTO createReservation(CreateReservationRequest request) {
        // 1. Get or Create Vehicle
        Vehicle vehicle = vehicleRepository.findByPlateNumber(request.getPlateNumber())
                .orElseGet(() -> {
                    VehicleType type = vehicleTypeRepository.findById(request.getVehicleTypeId())
                            .orElseThrow(() -> new RuntimeException("An error occurred"));
                    Vehicle newVehicle = Vehicle.builder()
                            .vehicleType(type)
                            .plateNumber(request.getPlateNumber())
                            .build();
                    return vehicleRepository.save(newVehicle);
                });

        // Check if there's an active or pending reservation for this vehicle
        List<Reservation> existing = reservationRepository.findByVehicle_PlateNumberAndStatus(request.getPlateNumber(), "PENDING");
        if (!existing.isEmpty()) {
            throw new IllegalStateException("Vehicle already has a pending reservation.");
        }

        // 2. Calculate Price dynamically
        BigDecimal fee = previewPrice(request.getVehicleTypeId(), request.getExpectedEntryTime(), request.getExpectedDurationMinutes());

        // 3. Find Zone and Check Capacity
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone not found"));
                
        BigDecimal occupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());
        if (occupancy.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalStateException("Zone is full. Cannot make a reservation.");
        }

        // 4. Create Reservation
        Reservation reservation = Reservation.builder()
                .vehicle(vehicle)
                .zone(zone)
                .expectedEntryTime(request.getExpectedEntryTime())
                .expectedDurationMinutes(request.getExpectedDurationMinutes())
                .status("PENDING") // PENDING means paid but hasn't entered
                .reservationFee(fee)
                .qrCode("QR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
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

        LocalDateTime expectedExitTime = reservation.getExpectedEntryTime().plusMinutes(reservation.getExpectedDurationMinutes());
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
                            .build();
                    return vehicleRepository.save(newVehicle);
                });

        reservation.setVehicle(vehicle);
        reservationRepository.save(reservation);

        return mapToDTO(reservation);
    }

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

        BigDecimal refundPercent = BigDecimal.ZERO;
        if (diffMins >= 30) {
            refundPercent = BigDecimal.ONE; // 100%
        } else if (diffMins > 0 && diffMins < 30) {
            refundPercent = new BigDecimal("0.5"); // 50%
        }

        BigDecimal amountPaid = reservation.getReservationFee() != null ? reservation.getReservationFee() : BigDecimal.ZERO;
        BigDecimal refundAmount = amountPaid.multiply(refundPercent);
        BigDecimal penaltyFee = amountPaid.subtract(refundAmount);

        reservation.setStatus("CANCELLED");
        reservation.setRefundAmount(refundAmount);
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            reservation.setRefundStatus("PENDING");
        }
        reservationRepository.save(reservation);

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email).orElse(null);

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
                        .cancelTime(now)
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
            transactionRepository.save(penaltyTx);
        }

        return mapToDTO(reservation);
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
        int windowMinutes = 30;
        try {
            windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES").getConfigValue());
        } catch (Exception e) {
            // ignore
        }

        LocalDateTime notifyTime = res.getExpectedEntryTime().minusMinutes(windowMinutes);
        LocalDateTime expireTime = res.getExpectedEntryTime().plusMinutes(res.getExpectedDurationMinutes());

        // Schedule Notification
        if (res.getNotifiedEarlyArrival() == null || !res.getNotifiedEarlyArrival()) {
            taskScheduler.schedule(() -> notifyStaffTask(res.getId()),
                    java.util.Date.from(notifyTime.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }

        // Schedule Expiration
        taskScheduler.schedule(() -> expireReservationTask(res.getId()),
                java.util.Date.from(expireTime.atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    @Transactional
    public void notifyStaffTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus()) || Boolean.TRUE.equals(res.getNotifiedEarlyArrival())) return;

        res.setNotifiedEarlyArrival(true);
        reservationRepository.save(res);

        if (res.getZone() != null && res.getZone().getFloor() != null) {
            Long floorId = res.getZone().getFloor().getId();
            String message = String.format("Vehicle %s is arriving soon for reservation at Zone %s.",
                    res.getVehicle().getPlateNumber(), res.getZone().getZoneName());
            Object payload = java.util.Map.of("message", message, "plateNumber", res.getVehicle().getPlateNumber(), "zoneName", res.getZone().getZoneName());
            messagingTemplate.convertAndSend("/topic/floors/" + floorId + "/notifications", payload);
        }
    }

    @Transactional
    public void expireReservationTask(Long reservationId) {
        Reservation res = reservationRepository.findById(reservationId).orElse(null);
        if (res == null || !"PENDING".equals(res.getStatus())) return;

        log.info("Reservation {} marked as COMPLETED_UNUSED (No-show)", res.getId());
        res.setStatus("COMPLETED_UNUSED");
        reservationRepository.save(res);
    }

    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime now = event.getNewSimulatedTime();
        log.info("Handling TimeFastForwardedEvent in ReservationService for time: {}", now);
        
        List<Reservation> pendingReservations = reservationRepository.findByStatus("PENDING");
        int expiredCount = 0;
        int notifiedCount = 0;
        
        int windowMinutes = 30;
        try {
            windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES").getConfigValue());
        } catch (Exception e) {
            // ignore
        }

        for (Reservation res : pendingReservations) {
            LocalDateTime expireTime = res.getExpectedEntryTime().plusMinutes(res.getExpectedDurationMinutes());
            if (now.isAfter(expireTime)) {
                log.info("Reservation {} marked as COMPLETED_UNUSED (No-show) due to time fast-forward", res.getId());
                res.setStatus("COMPLETED_UNUSED");
                reservationRepository.save(res);
                expiredCount++;
            } else {
                LocalDateTime notifyTime = res.getExpectedEntryTime().minusMinutes(windowMinutes);
                if (now.isAfter(notifyTime) && (res.getNotifiedEarlyArrival() == null || !res.getNotifiedEarlyArrival())) {
                    res.setNotifiedEarlyArrival(true);
                    reservationRepository.save(res);
                    notifiedCount++;
                }
            }
        }
        
        if (expiredCount > 0 || notifiedCount > 0) {
            log.info("Fast-forward summary: Expired {} reservations, Notified {} early arrivals", expiredCount, notifiedCount);
        }
    }

    private ReservationDTO mapToDTO(Reservation reservation) {
        return ReservationDTO.builder()
                .id(reservation.getId())
                .plateNumber(reservation.getVehicle().getPlateNumber())
                .vehicleType(reservation.getVehicle().getVehicleType().getTypeName())
                .zoneName(reservation.getZone() != null ? reservation.getZone().getZoneName() : "N/A")
                .slotName("N/A") // Slots are assigned dynamically by IoT
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

    @org.springframework.transaction.annotation.Transactional
    public ReservationDTO resolveZone(Long reservationId, Long newZoneId, ReservationConflictScheduler scheduler) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
                
        if (!"PENDING".equals(reservation.getStatus())) {
            throw new IllegalStateException("Reservation is not PENDING");
        }

        Zone newZone = zoneRepository.findById(newZoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found"));

        if (!newZone.getVehicleType().getId().equals(reservation.getVehicle().getVehicleType().getId())) {
            throw new IllegalArgumentException("Zone vehicle type does not match reservation vehicle type");
        }

        reservation.setZone(newZone);
        reservationRepository.save(reservation);
        
        // Remove from notified list so scheduler can check it again if needed
        if (scheduler != null) {
            scheduler.removeNotificationFlag(reservationId);
        }

        return mapToDTO(reservation);
    }
}
