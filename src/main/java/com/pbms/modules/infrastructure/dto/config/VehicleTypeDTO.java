/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Vehicle Type.
 * @Dependencies: lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor
 */
package com.pbms.modules.infrastructure.dto.config;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK
// =========================================================================
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU CẤU HÌNH LOẠI PHƯƠNG TIỆN (VEHICLE TYPE DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng DTO đại diện cho một loại phương tiện được bãi xe hỗ trợ tiếp nhận
 * (ví dụ: Ô tô 4 chỗ, Xe máy tay ga), định nghĩa kích thước chiếm dụng trên lưới bản đồ.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Các chỉ số `matrixWidth` và `matrixHeight` cho phép bãi xe hỗ trợ
 *   nhiều kích cỡ xe khác nhau (xe tải chiếm nhiều ô hơn xe máy) trên sơ đồ lưới Konva.
 * - Minh chứng 2: Cờ `hasMapSlots` chỉ định loại xe này có cần vẽ từng ô Slot cụ thể
 *   hay chỉ cần tính theo sức chứa chung của khu vực.
 * =========================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTypeDTO {
    private Long id;             // Mã định danh loại phương tiện
    private String typeName;     // Tên loại xe (ví dụ: "Ô tô", "Xe máy")
    private Integer matrixWidth; // Chiều rộng chiếm dụng trên lưới sơ đồ
    private Integer matrixHeight;// Chiều cao chiếm dụng trên lưới sơ đồ
    private String category;     // Phân loại nhóm xe: FOUR_WHEEL, TWO_WHEEL
    private String status;       // Trạng thái cấu hình: ACTIVE, DELETED
    private String iconUrl;      // Đường dẫn icon biểu tượng xe trên UI
    private Boolean hasMapSlots; // Có vẽ chi tiết từng ô Slot hay không (true/false)
}

