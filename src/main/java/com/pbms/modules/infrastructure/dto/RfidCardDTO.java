/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: DTO cho màn hình "Card Warehouse" của manager - visualId và
 * location là giá trị suy luận, không lưu trong DB.
 * @Dependencies: Không có
 */
package com.pbms.modules.infrastructure.dto;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK
// =========================================================================
import lombok.Builder;
import lombok.Data;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU THẺ RFID / THẺ TỪ BÃI XE (RFID CARD DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng trao đổi dữ liệu (DTO) gửi về cho Frontend hoặc nhận từ các API giám sát,
 * đóng gói thông tin thẻ RFID đang hoạt động trong bãi xe.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `uid` (tương ứng `cardCode` bên trong chip vô tuyến) và `visualId`
 *   (tương ứng `cardId` in trên mặt thẻ) được tách biệt rõ ràng để UI vừa hiển thị thân thiện,
 *   vừa duy trì định danh đúng với thiết bị phần cứng RFID.
 * =========================================================================================
 */
@Data
@Builder
public class RfidCardDTO {
    private String uid;       // Mã chip UID đọc từ đầu đọc vô tuyến (A1B2C3D4...)
    private String visualId;  // Mã hiệu hiển thị/in trên thẻ (CARD-001...)
    private String status;    // Trạng thái thẻ: AVAILABLE, IN_USE, LOST, DAMAGED
    private String location;  // Vị trí hoặc khu vực đang ghi nhận thẻ
}

