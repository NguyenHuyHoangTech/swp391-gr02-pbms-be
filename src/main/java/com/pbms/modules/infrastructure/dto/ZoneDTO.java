/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Zone entity.
 * @Dependencies: lombok.Builder, lombok.Data, java.util.List
 */
package com.pbms.modules.infrastructure.dto;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK VÀ JAVA CHUẨN
// =========================================================================
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU HIỂN THỊ TỔNG HỢP KHU VỰC BÃI XE (ZONE DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng trung gian (DTO) tổng hợp toàn bộ trạng thái không gian đỗ xe của một
 * khu vực (`Zone`), bao gồm sức chứa, số chỗ trống thực tế, số lượt đặt trước sắp tới
 * (`pendingReservations`) và danh sách các ô đỗ con (`slots`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Các số liệu `capacity` (tổng sức chứa) và `availableSlots` (ô trống)
 *   được tính toán động trong `ZoneService` dựa trên số lượng Slot `AVAILABLE` trừ đi
 *   `pendingReservations` (vé đặt chỗ trước trong 30 phút).
 * - Minh chứng 2: Tọa độ (`layoutX`, `layoutY`, `rotation`) được dùng để vẽ khu
 *   vực lên bản đồ Konva SVG. Kích thước ô đỗ KHÔNG nằm trong DTO này — Frontend
 *   tra `matrixWidth`/`matrixHeight` từ danh sách `vehicleTypes` theo
 *   `vehicleTypeId`, nên ở đây không cần lặp lại 2 trường đó.
 * =========================================================================================
 */
@Data
@Builder
public class ZoneDTO {
    private Long id;                     // Mã định danh khu vực
    private Long floorId;                // Mã định danh tầng chứa khu vực
    private String floorName;            // Tên tầng hiển thị
    private String name;                 // Tên khu vực đỗ xe
    private Integer capacity;            // Tổng sức chứa (tính bằng tổng số ô đỗ thuộc Zone)
    private Integer availableSlots;      // Số ô trống thực tế sau khi trừ đi các đặt chỗ đang chờ
    private Integer pendingReservations; // Số lượng xe đã đặt chỗ trước đang trên đường tới (trong vòng 30 phút)
    private Long vehicleTypeId;          // Mã loại xe cho phép
    private String vehicleType;          // Mã chữ của loại xe: CAR hoặc MOTORBIKE
    private String functionType;         // Chức năng khu vực: WALK_IN (vãng lai), MONTHLY (vé tháng), BACKUP (dự phòng)
    private Double layoutX;              // Tọa độ X gốc trên bản đồ Konva SVG
    private Double layoutY;              // Tọa độ Y gốc trên bản đồ Konva SVG
    private Integer rotation;            // Góc quay khu vực trên bản đồ
    private List<SlotDTO> slots;         // Danh sách ô đỗ con bên trong khu vực
}

