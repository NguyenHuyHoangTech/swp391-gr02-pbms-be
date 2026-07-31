/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Slot Configuration.
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
 * GÓI DỮ LIỆU CẤU HÌNH Ô ĐỖ XE (SLOT CONFIG DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng DTO đại diện cho cấu hình của một ô đỗ xe nằm trong `ZoneConfigDTO`,
 * hỗ trợ chỉnh sửa trạng thái hoạt động trên giao diện cấu hình bản đồ.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `status` cho phép quản trị viên khóa tạm thời ô đỗ
 *   (`DISABLED`) để bảo trì sửa chữa mà không cần xóa bản ghi trong DB.
 * =========================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotConfigDTO {
    private Long id;       // Mã định danh ô đỗ
    private String name;   // Tên/kí hiệu ô đỗ (ví dụ: "A01")
    private String status; // Trạng thái: EMPTY, OCCUPIED, DISABLED
}
