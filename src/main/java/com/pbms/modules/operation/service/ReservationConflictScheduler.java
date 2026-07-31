/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Scheduled service that runs every minute to detect zone capacity
 *               conflicts for upcoming PENDING reservations. Notifies staff via
 *               WebSocket when a zone is full before a customer's arrival.
 * @Dependencies:
 *  - ReservationRepository (Local)
 *  - ZoneRoutingService    (com.pbms.modules.operation)
 *  - SimpMessagingTemplate (Spring WebSocket)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.finance.domain.Transaction;
import com.pbms.modules.finance.repository.TransactionRepository;
import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import com.pbms.modules.operation.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ReservationConflictScheduler {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ZoneRoutingService zoneRoutingService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /**
     * In-memory map to store the processed status of each reservation.
     * Key: reservationId | Value: "CONFLICT" or "RESERVED"
     * This prevents duplicate WebSocket notifications every minute.
     */
    private final Map<Long, String> notifiedReservations = new ConcurrentHashMap<>();

    /**
     * @Function: detectZoneConflicts
     * @Description: Runs every minute. Checks upcoming PENDING reservations within
     *               the time window and notifies staff if their target zone is full.
     * @Logic_Steps:
     * 1. Define checking window: current time to 30 minutes in the future.
     * 2. Query PENDING reservations expected to arrive within this window.
     * 3. Loop through each reservation:
     *    3.1. Fetch routing status for the zone to check available slots.
     *    3.2. If available slots <= 0 and not notified yet, send CONFLICT alert to staff.
     *    3.3. If slots are available and not reserved yet, send RESERVED notification.
     * @returns void
     */
    @Transactional
    @Scheduled(cron = "0 * * * * *")
    public void detectZoneConflicts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowEnd = now.plusMinutes(30);

            List<Reservation> upcoming = reservationRepository
                    .findUpcomingReservations("PENDING", now, windowEnd);

            for (Reservation r : upcoming) {
                Long zoneId = r.getZone().getId();
                Long vehicleTypeId = r.getVehicle().getVehicleType().getId();

                List<ZoneRoutingStatusDTO> statuses = zoneRoutingService
                        .getRoutingStatus(vehicleTypeId, "BOOK", r.getZone().getId());

                boolean isFull = statuses.stream()
                        .filter(s -> s.getZoneId().equals(zoneId))
                        .findFirst()
                        .map(s -> s.getAvailable() <= 0)
                        .orElse(false);

                String currentState = notifiedReservations.get(r.getId());

                if (isFull && !"CONFLICT".equals(currentState)) {
                    notifiedReservations.put(r.getId(), "CONFLICT");

                    if (messagingTemplate != null) {
                        messagingTemplate.convertAndSend("/topic/staff/notifications", (Object) Map.of(
                                "type", "ZONE_CONFLICT",
                                "reservationId", r.getId(),
                                "zoneName", r.getZone().getZoneName(),
                                "plate", r.getVehicle().getPlateNumber(),
                                "vehicleTypeId", vehicleTypeId,
                                "message", "Zone is FULL for upcoming reservation. Please resolve!"
                        ));
                    }

                } else if (!isFull && !"RESERVED".equals(currentState)) {
                    notifiedReservations.put(r.getId(), "RESERVED");

                    if (messagingTemplate != null) {
                        messagingTemplate.convertAndSend("/topic/staff/notifications", (Object) Map.of(
                                "type", "ZONE_RESERVED",
                                "reservationId", r.getId(),
                                "zoneName", r.getZone().getZoneName(),
                                "plate", r.getVehicle().getPlateNumber(),
                                "vehicleTypeId", vehicleTypeId,
                                "message", "Virtual slot reserved successfully."
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in detectZoneConflicts scheduler", e);
        }
    }

    /**
     * @Function: expireUnusedReservations
     * @Description: Runs every minute. Marks PENDING reservations as COMPLETED_UNUSED
     *               if their expectedEntryTime + duration has already passed.
     * @Logic_Steps:
     * 1. Query all PENDING reservations.
     * 2. Loop through reservations:
     *    2.1. Calculate the expire time (expectedEntryTime + expectedDurationMinutes).
     *    2.2. If current time is past the expire time, update status to COMPLETED_UNUSED.
     *    2.3. If reservation had a fee, record it as a penalty transaction (revenue).
     * @returns void
     */
    @Transactional
    @Scheduled(cron = "0 * * * * *")
    public void expireUnusedReservations() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Reservation> pending = reservationRepository.findByStatus("PENDING");

            for (Reservation r : pending) {
                if (r.getExpectedEntryTime() == null) continue;
                int duration = r.getExpectedDurationMinutes() != null ? r.getExpectedDurationMinutes() : 120;

                LocalDateTime expireTime = r.getExpectedEntryTime().plusMinutes(duration);

                if (now.isAfter(expireTime)) {
                    log.info("Reservation {} expired without arrival, marking COMPLETED_UNUSED", r.getId());
                    r.setStatus("COMPLETED_UNUSED");
                    reservationRepository.save(r);

                    BigDecimal penaltyFee = r.getReservationFee() != null ? r.getReservationFee() : BigDecimal.ZERO;
                    if (penaltyFee.compareTo(BigDecimal.ZERO) > 0) {
                        Transaction penaltyTx = Transaction.builder()
                                .amount(penaltyFee)
                                .paymentMethod("GATEWAY")
                                .status("SUCCESS")
                                .transactionReference("PENALTY-RES-" + r.getId())
                                .build();

                        penaltyTx.setCreatedAt(now);
                        penaltyTx = transactionRepository.save(penaltyTx);

                        transactionRepository.updateCreatedAtNative(penaltyTx.getId(), now);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in expireUnusedReservations scheduler", e);
        }
    }

    /**
     * @Function: attemptResolveConflict
     * @Description: Called when staff manually resolves a conflict from the UI.
     * @Logic_Steps:
     * 1. Verify reservation exists and is PENDING.
     * 2. Re-check the routing status for the zone to ensure it is not full.
     * 3. If still full, throw exception.
     * 4. If available, update notification state to RESERVED and broadcast success.
     * @param reservationId - ID of the reservation
     * @returns void
     */
    @Transactional
    public void attemptResolveConflict(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!"PENDING".equals(r.getStatus())) {
            throw new IllegalStateException("Reservation is not PENDING");
        }

        Long zoneId = r.getZone().getId();
        Long vehicleTypeId = r.getVehicle().getVehicleType().getId();

        boolean isFull = zoneRoutingService.getRoutingStatus(vehicleTypeId, "BOOK", r.getZone().getId())
                .stream()
                .filter(s -> s.getZoneId().equals(zoneId))
                .findFirst()
                .map(s -> s.getAvailable() <= 0)
                .orElse(true);

        if (isFull) {
            throw new IllegalStateException("Zone is still full!");
        }

        notifiedReservations.put(r.getId(), "RESERVED");

        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/staff/notifications", (Object) Map.of(
                    "type", "ZONE_RESERVED",
                    "reservationId", r.getId(),
                    "zoneName", r.getZone().getZoneName(),
                    "plate", r.getVehicle().getPlateNumber(),
                    "vehicleTypeId", vehicleTypeId,
                    "message", "Virtual slot reserved successfully."
            ));
        }
    }

    public void removeNotificationFlag(Long reservationId) {
        notifiedReservations.remove(reservationId);
    }
}
