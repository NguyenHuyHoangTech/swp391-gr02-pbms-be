/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-06
 * @Description: Thực thể (Entity) đại diện cho Vé Tháng (Monthly Ticket) trong cơ sở dữ liệu.
 *               Lưu trữ thông tin liên kết giữa người dùng, xe, loại xe và thời hạn của vé.
 * @Dependencies: 
 * - BaseEntity (Kế thừa các trường common như id, createdAt, updatedAt)
 */
package com.pbms.modules.operation.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyTicket extends BaseEntity {

    // Liên kết với bảng người dùng (chủ sở hữu vé)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Biển số xe được đăng ký cho vé tháng này
    @Column(name = "plate_number", nullable = false, length = 50)
    private String plateNumber;

    // Liên kết với bảng loại xe (VD: Xe máy, Ô tô) để tính giá tiền và kiểm tra logic
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    // Thời điểm bắt đầu có hiệu lực của vé
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    // Thời điểm hết hạn của vé
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    // Trạng thái hiện tại của vé: ACTIVE (Đang hoạt động), EXPIRED (Hết hạn), CANCELLED (Bị hủy)
    @Column(nullable = false, length = 50)
    private String status;
}
