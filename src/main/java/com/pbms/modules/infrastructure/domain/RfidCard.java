/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Entity đại diện cho 1 thẻ RFID vật lý trong kho (bảng
 * rfid_cards) - dùng để gắn với xe khi check-in, thu hồi khi check-out.
 * @Dependencies: Không có
 */
package com.pbms.modules.infrastructure.domain;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN JPA VÀ LOMBOK
// =========================================================================
import jakarta.persistence.*;
import lombok.*;

/**
 * =========================================================================================
 * THỰC THỂ THẺ RFID / THẺ TỪ BÃI XE (RFID CARD DOMAIN ENTITY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Ánh xạ bảng `rfid_cards` trong cơ sở dữ liệu, quản lý danh sách các thẻ từ/RFID
 * được sử dụng để kiểm soát phương tiện ra vào bãi xe. Phân biệt giữa mã in trên
 * mặt thẻ (`cardId`) và mã chuỗi chip vô tuyến bên trong (`cardCode`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trạng thái thẻ `status` phản ánh khả năng sử dụng: `AVAILABLE` (rảnh
 *   tại quầy), `IN_USE` (đang gắn với một xe trong bãi), `LOST`/`DAMAGED` (bị khóa).
 * - Minh chứng 2: Tích hợp với thiết bị IoT phần cứng (`IotHardwareController`) thông
 *   qua `cardCode` đọc được từ đầu đọc thẻ RFID tại cổng.
 * =========================================================================================
 */
@Entity
@Table(name = "rfid_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Mã định danh nội bộ trong DB

    @Column(name = "card_id", unique = true)
    private String cardId; // Mã hiển thị/in trên mặt thẻ (ví dụ: "CARD-001")

    @Column(name = "card_code", unique = true, nullable = false)
    private String cardCode; // Mã chip vô tuyến UID duy nhất đọc từ thiết bị RFID (ví dụ: "A1B2C3D4")

    @Column(name = "status")
    private String status; // AVAILABLE, IN_USE, LOST, DAMAGED

    @Column(name = "assigned_plate", length = 50)
    private String assignedPlate; // Biển số xe cố định được gán (dành cho thẻ xe tháng hoặc vé dài hạn)
}

