/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller nhận sự kiện cảm biến IoT khi 1 chỗ đỗ đổi trạng
 * thái, và cho phép nhân viên/manager đổi trạng thái slot thủ công (VD bật
 * chế độ bảo trì) từ màn hình quản lý.
 * @Dependencies:
 * - SlotRepository (Local)
 * - ZoneRoutingService, ZoneOccupancyTracker (Local)
 */
package com.pbms.modules.infrastructure.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.infrastructure.domain.Slot;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import com.pbms.modules.operation.service.ZoneOccupancyTracker;
import com.pbms.modules.operation.service.ZoneRoutingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import com.pbms.common.annotation.LogAudit;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/infrastructure/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotRepository slotRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ZoneRoutingService zoneRoutingService;
    private final ZoneOccupancyTracker zoneOccupancyTracker;

    /**
     * @Function: iotUpdate
     * @Description: Cảm biến IoT gọi API này mỗi khi phát hiện 1 chỗ đỗ
     * chuyển trạng thái (có xe vào / trống chỗ).
     * @Logic_Steps:
     * 1. Cập nhật trạng thái slot vào DB.
     * 2. Cập nhật mốc occupancy cao nhất (high-water mark) của zone chứa slot.
     * 3. Báo realtime cho mọi màn hình đang mở qua WebSocket.
     */
    @PostMapping("/iot-update")
    public ResponseEntity<ApiResponse<String>> iotUpdate(@RequestBody IotUpdateRequest request) {
        Slot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        if (!"EMPTY".equals(request.getStatus()) && !"OCCUPIED".equals(request.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid status"));
        }

        slot.setStatus(request.getStatus());
        slotRepository.save(slot);

        java.math.BigDecimal currentOccupancy = zoneRoutingService.calculateZoneOccupancy(slot.getZone().getId());
        zoneOccupancyTracker.updateOccupancy(slot.getZone().getId(), currentOccupancy);

        messagingTemplate.convertAndSend("/topic/map-updates", slot);

        return ResponseEntity.ok(ApiResponse.success("Slot updated", "Success"));
    }

    /**
     * @Function: updateSlotStatus
     * @Description: Nhân viên/manager chủ động đổi trạng thái 1 slot thủ
     * công - khác iot-update là do cảm biến tự động báo về. Chỉ chấp nhận
     * EMPTY/DISABLED vì OCCUPIED phải do đúng luồng check-in xe gán.
     */
    @PutMapping("/{id}/status")
    @LogAudit(action = "UPDATE", resource = "Slot", description = "Update slot status manually")
    public ResponseEntity<ApiResponse<String>> updateSlotStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        String status = body.get("status");
        if (status == null || (!status.equals("EMPTY") && !status.equals("DISABLED"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid status"));
        }

        slot.setStatus(status);
        slotRepository.save(slot);

        messagingTemplate.convertAndSend("/topic/map-updates", slot);

        return ResponseEntity.ok(ApiResponse.success("Slot status updated", "Success"));
    }

    @Data
    public static class IotUpdateRequest {
        private Long slotId;
        private String status;
    }
}
