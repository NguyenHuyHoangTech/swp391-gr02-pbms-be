/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Scheduled service that runs every minute to detect zone capacity
 *               conflicts for upcoming PENDING reservations. Notifies staff via
 *               WebSocket when a zone is full before a customer's arrival.
 * @Dependencies:
 *  - ReservationRepository (Local)
 *  - ZoneRoutingService    (com.pbms.modules.operation)
 *  - SimpMessagingTemplate (Spring WebSocket)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import com.pbms.modules.operation.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /**
     * In-memory map lưu trạng thái đã xử lý của từng reservation.
     * Key: reservationId | Value: "CONFLICT" hoặc "RESERVED"
     * Tránh gửi WebSocket trùng lặp mỗi phút.
     */
    private final Map<Long, String> notifiedReservations = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // SCHEDULED JOBS
    // -------------------------------------------------------------------------

    /**
     * @Function: detectZoneConflicts
     * @Description: Runs every minute. Checks upcoming PENDING reservations within
     *               the time window and notifies staff if their target zone is full.
     */
    @Transactional
    @Scheduled(cron = "0 * * * * *")
    public void detectZoneConflicts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowEnd = now.plusMinutes(30);

            // Tìm các xe sắp vào trong 30 phút tới
            List<Reservation> upcoming = reservationRepository
                    .findUpcomingReservations("PENDING", now, windowEnd);

            for (Reservation r : upcoming) {
                Long zoneId = r.getZone().getId();
                Long vehicleTypeId = r.getVehicle().getVehicleType().getId();

                // Lấy danh sách chỗ trống từ Member 2 (Routing Service)
                List<ZoneRoutingStatusDTO> statuses = zoneRoutingService
                        .getRoutingStatus(vehicleTypeId, "BOOK", r.getZone().getId());

                // Kiểm tra xem Zone đó có đang đầy (available <= 0) không?
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

                // Nếu thời điểm hiện tại đã vượt qua hạn chót -> Hủy bỏ (No-Show)
                if (now.isAfter(expireTime)) {
                    log.info("Reservation {} expired without arrival, marking COMPLETED_UNUSED", r.getId());
                    r.setStatus("COMPLETED_UNUSED");
                    reservationRepository.save(r);
                }
            }
        } catch (Exception e) {
            log.error("Error in expireUnusedReservations scheduler", e);
        }
    }

    // -------------------------------------------------------------------------
    // PUBLIC METHODS (gọi từ Controller)
    // -------------------------------------------------------------------------

    /**
     * @Function: attemptResolveConflict
     * @Description: Dành cho lúc Staff nhận được thông báo lỗi đỏ,
     *               họ xử lý dọn dẹp Zone xong rồi bấm nút "Resolve" trên FE.
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

        // Check lại xem Zone đã trống thật chưa
        boolean isFull = zoneRoutingService.getRoutingStatus(vehicleTypeId, "BOOK", r.getZone().getId())
                .stream()
                .filter(s -> s.getZoneId().equals(zoneId))
                .findFirst()
                .map(s -> s.getAvailable() <= 0)
                .orElse(true);

        if (isFull) {
            throw new IllegalStateException("Zone is still full!");
        }

        // Đã trống -> Resolve thành công
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
