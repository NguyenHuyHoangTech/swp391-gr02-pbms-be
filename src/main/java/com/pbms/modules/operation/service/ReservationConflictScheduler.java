package com.pbms.modules.operation.service;

import com.pbms.modules.system.service.SystemConfigService;
import com.pbms.modules.operation.domain.Reservation;
import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import com.pbms.modules.operation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationConflictScheduler {

    private final ReservationRepository reservationRepository;
    private final ZoneRoutingService zoneRoutingService;
    private final SystemConfigService systemConfigService;
    private final SimpMessagingTemplate messagingTemplate;

    // Track notified reservations to avoid spamming the websocket every minute
    private final Set<Long> notifiedReservations = ConcurrentHashMap.newKeySet();

    @Scheduled(cron = "0 * * * * *") // Run every minute
    public void detectZoneConflicts() {
        try {
            int windowMinutes = 30; // default
            try {
                String configVal = systemConfigService.getConfigByKey("RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES").getConfigValue();
                if (configVal != null) {
                    windowMinutes = Integer.parseInt(configVal);
                }
            } catch (Exception e) {
                log.warn("Could not read RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES, using default 30", e);
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowEnd = now.plusMinutes(windowMinutes);

            List<Reservation> upcomingReservations = reservationRepository.findUpcomingReservations("PENDING", now, windowEnd);

            for (Reservation r : upcomingReservations) {
                if (notifiedReservations.contains(r.getId())) {
                    continue; // Already notified
                }

                // Check if the requested zone is full
                Long zoneId = r.getZone().getId();
                Long floorId = r.getZone().getFloor().getId();
                Long vehicleTypeId = r.getVehicle().getVehicleType().getId();

                // Get routing status to see available capacity
                List<ZoneRoutingStatusDTO> statuses = zoneRoutingService.getRoutingStatus(vehicleTypeId, "BOOK");
                
                boolean isFull = false;
                for (ZoneRoutingStatusDTO status : statuses) {
                    if (status.getZoneId().equals(zoneId)) {
                        // If available slots is <= 0, the zone is full!
                        if (status.getAvailable() <= 0) {
                            isFull = true;
                        }
                        break;
                    }
                }

                if (isFull) {
                    log.warn("Reservation {} is approaching but Zone {} is FULL!", r.getId(), r.getZone().getZoneName());
                    
                    // Mark as notified
                    notifiedReservations.add(r.getId());

                    // Send notification to all staff
                    String destination = "/topic/staff/notifications";
                    String message = String.format("{\"type\":\"ZONE_CONFLICT\", \"reservationId\":%d, \"zoneName\":\"%s\", \"customer\":\"%s\", \"plate\":\"%s\", \"vehicleTypeId\":%d, \"message\":\"Zone is FULL for upcoming reservation. Please resolve!\"}",
                            r.getId(),
                            r.getZone().getZoneName(),
                            r.getVehicle().getUser() != null ? r.getVehicle().getUser().getFullName() : "Guest",
                            r.getVehicle().getPlateNumber(),
                            vehicleTypeId
                    );
                    
                    messagingTemplate.convertAndSend(destination, message);
                }
            }
        } catch (Exception e) {
            log.error("Error in ReservationConflictScheduler", e);
        }
    }

    public void removeNotificationFlag(Long reservationId) {
        notifiedReservations.remove(reservationId);
    }
}
