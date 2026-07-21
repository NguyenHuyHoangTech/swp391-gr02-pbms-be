/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-22
 * @Description: Xử lý sự kiện cảm biến slot (từ phần cứng IoT / mô phỏng) và
 * phát cảnh báo realtime. 2 nhiệm vụ: (1) cập nhật trạng thái slot vào DB +
 * bắn WebSocket để bản đồ FE đổi màu ngay; (2) giám sát riêng cho zone
 * MONTHLY - phát hiện xe vãng lai đỗ lụi vào chỗ thuê tháng rồi cảnh báo
 * quản lý.
 * @Dependencies:
 * - SlotRepository (Local)
 * - ParkingSessionRepository (Local)
 * - WebSocketEventPublisher (Local)
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.infrastructure.domain.Slot;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.infrastructure.service.WebSocketEventPublisher;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class ZoneMonitoringService {

    private final SlotRepository slotRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final WebSocketEventPublisher eventPublisher;

    public ZoneMonitoringService(SlotRepository slotRepository,
            ParkingSessionRepository parkingSessionRepository, WebSocketEventPublisher eventPublisher) {
        this.slotRepository = slotRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @Function: processSensorEvent
     * @Description: Nhận 1 sự kiện cảm biến (sensorId = id của slot, status
     * mới) và xử lý.
     * @Logic_Steps:
     * 1. Cập nhật trạng thái slot vào DB.
     * 2. Bắn cập nhật slot realtime tới bản đồ FE qua WebSocket.
     * 3. Nếu xe vừa đỗ vào (OCCUPIED) 1 zone MONTHLY: đếm slot MONTHLY đang
     *    có xe, đối chiếu số xe thuê tháng thực sự đang trong bãi - lệch thì
     *    phát cảnh báo (nghi xe vãng lai đỗ lụi).
     */
    @Transactional
    public void processSensorEvent(String sensorId, String status) {
        Long slotId = Long.parseLong(sensorId);
        Slot slot = slotRepository.findById(slotId).orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        slot.setStatus(status);
        slotRepository.save(slot);

        Zone zone = slot.getZone();

        Map<String, Object> slotPayload = new HashMap<>();
        slotPayload.put("slotId", slot.getId());
        slotPayload.put("zoneId", zone.getId());
        slotPayload.put("status", slot.getStatus());
        eventPublisher.broadcastEvent("/topic/slots/status", "SLOT_UPDATED", slotPayload);

        if ("OCCUPIED".equals(status) && "MONTHLY".equals(zone.getFunctionType())) {
            long occupiedMonthlySlots = slotRepository.countByFunctionTypeAndVehicleTypeIdAndStatus("MONTHLY",
                    zone.getVehicleType().getId(), "OCCUPIED");
            long activeMonthlyCars = parkingSessionRepository
                    .countActiveMonthlyCarsByVehicleType(zone.getVehicleType().getId(), com.pbms.common.utils.TimeProvider.now());

            if (occupiedMonthlySlots > activeMonthlyCars) {
                Map<String, Object> alertPayload = new HashMap<>();
                alertPayload.put("zoneId", zone.getId());
                alertPayload.put("zoneName", zone.getZoneName());
                alertPayload.put("message", "Overload Alert: There are " + occupiedMonthlySlots
                        + " slots occupied in the Monthly Zone (type " + zone.getVehicleType().getTypeName()
                        + "), but only " + activeMonthlyCars
                        + " monthly cars of this type are currently in the parking lot! Walk-in vehicles might have parked improperly.");

                eventPublisher.broadcastCriticalEvent("/topic/alerts", "ZONE_VIOLATION", alertPayload);
            }
        }
    }
}
