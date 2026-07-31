/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Floor Configuration.
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
 * GÓI DỮ LIỆU CẤU HÌNH TẦNG BÃI XE (FLOOR CONFIG DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng DTO sử dụng trong API quản trị sơ đồ bãi xe (`MapConfigurationController`),
 * cho phép cấu hình kích thước ma trận (`mapCols`, `mapRows`) và tên gọi từng tầng.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `type` (`FOUR_WHEEL`, `TWO_WHEEL`) phân loại tầng dành riêng
 *   cho ô tô hoặc xe máy, phục vụ Frontend khởi tạo lưới vẽ tương ứng.
 * =========================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorConfigDTO {
    private Long id;         // Mã định danh tầng
    private String name;     // Tên tầng (ví dụ: "Tầng Hầm B1")
    private String type;     // Loại tầng xe: FOUR_WHEEL (ô tô), TWO_WHEEL (xe máy)
    private Integer mapCols; // Số cột của lưới sơ đồ
    private Integer mapRows; // Số hàng của lưới sơ đồ
}

