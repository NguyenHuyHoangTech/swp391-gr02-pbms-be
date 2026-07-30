/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: Entity representing a specific parking Slot within a Zone. 
 *               Mapped to the 'slots' table in the database.
 * @Dependencies: 
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */  
package com.pbms.modules.infrastructure.domain;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN JPA VÀ LOMBOK
// =========================================================================
import jakarta.persistence.*;
import lombok.*;

/**
 * =========================================================================================
 * THỰC THỂ Ô ĐỖ XE / VỊ TRÍ ĐỖ (SLOT DOMAIN ENTITY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Ánh xạ bảng `slots` trong cơ sở dữ liệu, đại diện cho một ô đỗ xe cụ thể nằm
 * trong một khu vực (`Zone`). Hỗ trợ chỉ mục (`idx_zone_status`) giúp tìm kiếm nhanh
 * các ô trống (`AVAILABLE`) trên bản đồ giám sát.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Cơ chế Khóa lạc quan (Optimistic Locking) với `@Version` ngăn chặn
 *   tình trạng xung đột đồng thời khi hai xe cùng lúc chọn/đặt vào một ô trống.
 * - Minh chứng 2: Tích hợp với màn hình hiển thị sơ đồ thời gian thực (`SpaceMapScreen.tsx`)
 *   thông qua các trạng thái `AVAILABLE`, `OCCUPIED`, `DISABLED` (bảo trì).
 * =========================================================================================
 */
@Entity
@Table(name = "slots", indexes = {
    @Index(name = "idx_zone_status", columnList = "zone_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Mã định danh duy nhất của ô đỗ xe

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone; // Khu vực (Zone) chứa ô đỗ xe này

    @Column(name = "slot_name", nullable = false, length = 50)
    private String slotName; // Tên/kí hiệu ô đỗ (ví dụ: "A01", "B12")

    @Column(length = 50)
    @Builder.Default
    private String status = "AVAILABLE"; // Trạng thái ô đỗ: AVAILABLE (trống), OCCUPIED (đang đỗ), DISABLED (bảo trì)

    @Version
    @Builder.Default
    private Integer version = 1; // Trường phiên bản phục vụ Optimistic Locking tránh xung đột đặt chỗ
}

