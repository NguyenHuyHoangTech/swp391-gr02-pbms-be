/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Slot entity.
 * @Dependencies: lombok.Builder, lombok.Data
 */
package com.pbms.modules.infrastructure.dto;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK
// =========================================================================
import lombok.Builder;
import lombok.Data;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU HIỂN THỊ Ô ĐỖ XE (SLOT DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng truyền tải dữ liệu (DTO) của ô đỗ xe, được trả về trong danh sách của
 * `ZoneDTO` phục vụ hiển thị bản đồ mặt bằng bãi đỗ xe.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `id` được chuyển thành chuỗi `String` (thay vì `Long` của DB)
 *   nhằm tương thích tuyệt đối với thuộc tính ID của các hình vẽ SVG/Konva trên
 *   Frontend (`SpaceMapScreen.tsx`).
 * - Minh chứng 2: Trường `status` hỗ trợ 3 trạng thái chuẩn UI: `EMPTY` (trống,
 *   tương ứng AVAILABLE), `OCCUPIED` (đang đỗ), `DISABLED` (khóa/bảo trì).
 * =========================================================================================
 */
@Data
@Builder
public class SlotDTO {
    private String id;     // Mã định danh chuỗi tương thích SVG Frontend (ví dụ: "A01" hoặc "1")
    private String name;   // Tên/kí hiệu ô đỗ hiển thị trên sơ đồ
    private String status; // Trạng thái ô đỗ trên UI: EMPTY, OCCUPIED, DISABLED
}

