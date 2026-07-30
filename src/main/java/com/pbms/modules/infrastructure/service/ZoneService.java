/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-05
 * @Description: Service for managing Zone operations including fetching zone data for maps with soft-delete filtering.
 * @Dependencies:
 * - ZoneRepository (com.pbms.modules.infrastructure.repository.ZoneRepository)
 * - SlotRepository (com.pbms.modules.infrastructure.repository.SlotRepository)
 * - ReservationRepository (com.pbms.modules.operation.repository.ReservationRepository)
 * - SystemConfigService (com.pbms.modules.system.service.SystemConfigService)
 */
package com.pbms.modules.infrastructure.service;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ DATA TRANSFER OBJECT (DTO)
// =========================================================================
import com.pbms.modules.infrastructure.domain.Slot; // Entity bảng slots (ô đỗ xe).
import com.pbms.modules.infrastructure.domain.Zone; // Entity bảng zones (khu vực đỗ xe).
import com.pbms.modules.infrastructure.dto.SlotDTO; // DTO ô đỗ xe gửi cho Client.
import com.pbms.modules.infrastructure.dto.ZoneDTO; // DTO tổng hợp thông tin khu vực gửi cho Client.

// =========================================================================
// PHẦN 2: REPOSITORY TRUY VẤN VÀ CẤU HÌNH HỆ THỐNG
// =========================================================================
import com.pbms.modules.infrastructure.repository.SlotRepository; // Kho truy vấn ô đỗ.
import com.pbms.modules.infrastructure.repository.ZoneRepository; // Kho truy vấn khu vực.
import com.pbms.modules.operation.repository.ReservationRepository; // Kho tra cứu đơn đặt chỗ trước (booking).
import com.pbms.modules.system.service.SystemConfigService; // Tra cứu tham số cấu hình hệ thống (thời gian đến sớm cho phép).

// =========================================================================
// PHẦN 3: LOMBOK VÀ SPRING BOOT SERVICE
// =========================================================================
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * =========================================================================================
 * DỊCH VỤ NGHIỆP VỤ TÍNH TOÁN VÀ QUẢN LÝ KHU VỰC ĐỖ XE (ZONE SERVICE)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Service này chịu trách nhiệm tính toán thời gian thực tổng sức chứa, số lượng ô
 * đỗ thực tế còn trống và số lượng xe đặt trước đang sắp tới của từng khu vực trong
 * bãi xe để hiển thị chính xác lên bản đồ bãi xe (SpaceMapScreen).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Không dùng các trường tĩnh dễ bị lệch (drift) trong DB mà tính toán
 *   động (dynamic calculation) số lượng chỗ trống từ danh sách Slot thực tế có trạng thái
 *   EMPTY hoặc AVAILABLE.
 * - Minh chứng 2: Trừ đi số lượng đặt chỗ (Reservation) ở trạng thái PENDING có thời gian
 *   dự kiến nằm trong khoảng khung giờ `RESERVATION_EARLY_MINS`, tránh việc khách đặt trước
 *   đến bãi lại không có chỗ đỗ.
 * =========================================================================================
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final SystemConfigService systemConfigService;

    /**
     * =========================================================================
     * API/HÀM: LẤY DANH SÁCH KHU VỰC VÀ TÍNH TOÁN SUẤT ĐỖ CHO BẢN ĐỒ
     * =========================================================================
     * MỤC ĐÍCH:
     * Tổng hợp danh sách Zone (trừ các Zone đã xóa), đếm số ô trống vật lý và
     * trừ đi suất đặt trước hợp lệ để ra số chỗ trống thực tế.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Lấy tất cả Zone từ DB, lọc bỏ các Zone có trạng thái "DELETED".
     * 2. Với mỗi Zone:
     *    a. Truy vấn danh sách Slot thuộc Zone (`slotRepository.findByZoneId`).
     *    b. Đếm số lượng Slot có trạng thái "EMPTY" hoặc "AVAILABLE".
     *    c. Đọc cấu hình `RESERVATION_EARLY_MINS` (mặc định 30 phút).
     *    d. Kiểm tra danh sách Reservation đang "PENDING" xem có bao nhiêu xe đang
     *       nằm trong khung thời gian hợp lệ sắp đến.
     *    e. Tính `availableSlots = Math.max(0, physicalAvailableSlots - pendingReservations)`.
     *    f. Ánh xạ danh sách Slot và thuộc tính Zone thành ZoneDTO.
     * 3. Trả về danh sách ZoneDTO.
     */
    public List<ZoneDTO> getMapZones() {
        List<Zone> zones = zoneRepository.findAll().stream()
                .filter(z -> !"DELETED".equals(z.getStatus()))
                .collect(Collectors.toList());
        
        return zones.stream().map(zone -> {
            List<Slot> slots = slotRepository.findByZoneId(zone.getId());
            long totalSlots = slots.size();
            // Đếm số lượng ô đỗ vật lý đang trống ("EMPTY") hoặc sẵn sàng sử dụng ("AVAILABLE")
            long physicalAvailableSlots = slots.stream().filter(s -> "EMPTY".equals(s.getStatus()) || "AVAILABLE".equals(s.getStatus())).count();
            
            // TÍNH TOÁN SUẤT ĐẶT TRƯỚC HỢP LỆ (VIRTUAL RESERVATION SUBTRACTION):
            // LƯU Ý KIẾN TRÚC: Khách đặt trước (PENDING) sẽ được giữ chỗ ảo trong khoảng thời gian
            // trước giờ đến dự kiến `RESERVATION_EARLY_MINS` (mặc định 30 phút).
            // Nếu khách chưa tới trong khung thời gian này, ô trống thực tế bị trừ đi để bảo đảm
            // khi khách lái xe tới bãi sẽ luôn còn suất đỗ tương ứng.
            int windowMinutes = 30;
            try { 
                String configVal = systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue();
                if (configVal != null) windowMinutes = Integer.parseInt(configVal);
            } catch (Exception e) {}
            
            java.time.LocalDateTime nowTime = com.pbms.common.utils.TimeProvider.now();
            final int fWin = windowMinutes;
            long countInWindow = reservationRepository.findByZoneIdAndStatus(zone.getId(), "PENDING").stream().filter(r -> {
                java.time.LocalDateTime startWindow = r.getExpectedEntryTime().minusMinutes(fWin);
                java.time.LocalDateTime endWindow = r.getExpectedEntryTime().plusMinutes(r.getExpectedDurationMinutes() != null ? r.getExpectedDurationMinutes() : 120);
                return !nowTime.isBefore(startWindow) && !nowTime.isAfter(endWindow);
            }).count();

            long pendingReservations = countInWindow;
            // Số chỗ trống cuối cùng hiển thị trên bản đồ không được nhỏ hơn 0
            long availableSlots = Math.max(0, physicalAvailableSlots - pendingReservations);

            List<SlotDTO> slotDTOs = slots.stream().map(s -> SlotDTO.builder()
                .id(String.valueOf(s.getId())) // FE expect string id để dễ render SVG/Konva
                .name(s.getSlotName())
                .status(s.getStatus())
                .build()
            ).collect(Collectors.toList());

            return ZoneDTO.builder()
                .id(zone.getId())
                .floorId(zone.getFloor().getId())
                .floorName(zone.getFloor().getFloorName())
                .name(zone.getZoneName())
                .capacity((int) totalSlots)
                .availableSlots((int) availableSlots)
                .pendingReservations((int) pendingReservations)
                .vehicleTypeId(zone.getVehicleType().getId())
                .vehicleType(zone.getVehicleType().getTypeName())
                .functionType(zone.getFunctionType())
                .layoutX(zone.getLayoutX())
                .layoutY(zone.getLayoutY())
                .rotation(zone.getRotation())
                .slots(slotDTOs)
                .build();
        }).collect(Collectors.toList());
    }
}

