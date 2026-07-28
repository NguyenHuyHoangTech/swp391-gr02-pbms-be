/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Thực thể (Entity) đại diện cho một Đơn Đặt Chỗ (Reservation/Prebooking).
 *               Lưu trữ thông tin chi tiết về xe, khu vực đặt chỗ, thời gian và trạng thái của đơn.
 * @Dependencies: 
 * - Vehicle (Local)
 * - Zone (Local)
 */
package com.pbms.modules.operation.domain;

import com.pbms.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation extends BaseEntity {

    // Liên kết với phương tiện thực hiện đặt chỗ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    // Liên kết với khu vực bãi đậu mà khách hàng muốn đặt (VD: Khu A, Khu B)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private com.pbms.modules.infrastructure.domain.Zone zone;

    // Thời gian khách hàng dự kiến sẽ lái xe vào bãi
    @Column(name = "expected_entry_time", nullable = false)
    private LocalDateTime expectedEntryTime;

    // Thời lượng khách hàng dự kiến sẽ gửi xe (tính bằng phút)
    @Column(name = "expected_duration_minutes", nullable = false)
    private Integer expectedDurationMinutes;

    // Trạng thái của đơn: PENDING (Chờ), ACTIVE (Đang gửi), COMPLETED (Hoàn thành), CANCELLED (Đã hủy), NO_SHOW (Không đến)
    @Column(nullable = false, length = 50)
    private String status;

    // Số tiền khách hàng đã thanh toán trước cho việc đặt chỗ
    @Column(name = "reservation_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservationFee;

    // Cờ đánh dấu đã gửi thông báo đến sớm hay chưa (dùng cho hệ thống scheduler)
    @Column(name = "notified_early_arrival")
    private Boolean notifiedEarlyArrival;
}
