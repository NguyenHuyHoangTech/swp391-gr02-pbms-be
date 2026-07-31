/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Zone Configuration.
 * @Dependencies: lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor, java.util.List
 */
package com.pbms.modules.infrastructure.dto.config;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK VÀ JAVA CHUẨN
// =========================================================================
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU CẤU HÌNH KHU VỰC ĐỖ XE (ZONE CONFIG DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng DTO phục vụ thiết lập cấu hình và quản trị khu vực đỗ xe trên sơ đồ,
 * chứa các chỉ số về sức chứa, loại chức năng (`functionType`) và số lượng đặt
 * chỗ trước đang kích hoạt (`activeReservationsCount`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `functionType` (`WALK_IN`, `MONTHLY`) giúp UI hiển thị màu sắc
 *   phân biệt khu vực cho khách vãng lai và vé tháng trên bản đồ.
 * - Minh chứng 2: Tọa độ (`layoutX`, `layoutY`, `rotation`) và danh sách `slots`
 *   cho phép bộ công cụ Konva SVG vẽ và tùy chỉnh từng ô đỗ trong khu vực.
 * =========================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneConfigDTO {
    private Long id;                          // Mã định danh khu vực
    private Long floorId;                     // Mã tầng chứa khu vực
    private String name;                      // Tên hiển thị khu vực
    private Integer capacity;                 // Sức chứa (tổng số ô đỗ)
    private Long vehicleTypeId;               // Mã định danh loại phương tiện cho phép
    private String vehicleTypeName;           // Tên loại phương tiện (Ô tô, Xe máy)
    private String vehicleCategory;           // Phân khúc xe (TWO_WHEEL, FOUR_WHEEL...)
    private String functionType;              // Loại chức năng: WALK_IN (vãng lai), MONTHLY (vé tháng)
    private Double layoutX;                   // Tọa độ X gốc trên sơ đồ Konva SVG
    private Double layoutY;                   // Tọa độ Y gốc trên sơ đồ Konva SVG
    private Integer rotation;                 // Góc quay khu vực trên bản đồ
    private Long activeReservationsCount;     // Số lượng đặt chỗ trước đang có hiệu lực
    private List<SlotConfigDTO> slots;        // Danh sách cấu hình các ô đỗ con
}
