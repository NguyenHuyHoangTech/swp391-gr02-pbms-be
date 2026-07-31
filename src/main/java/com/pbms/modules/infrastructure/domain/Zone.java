/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: Entity representing a parking Zone (e.g., Zone A, Zone B). 
 *               Mapped to the 'zones' table in the database.
 * @Dependencies: 
 * - Floor (com.pbms.modules.infrastructure.domain.Floor)
 * - VehicleType (com.pbms.modules.operation.domain.VehicleType)
 */  
package com.pbms.modules.infrastructure.domain;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN JPA / LOMBOK
// =========================================================================
import com.pbms.modules.operation.domain.VehicleType;
import jakarta.persistence.*;
import lombok.*;

/**
 * =========================================================================================
 * THỰC THỂ KHU VỰC ĐỖ XE (ZONE DOMAIN ENTITY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Ánh xạ bảng `zones` trong cơ sở dữ liệu, phân chia không gian của một tầng (`Floor`)
 * thành các khu vực nhỏ hơn, chuyên biệt hóa theo loại phương tiện (`VehicleType`)
 * và mục đích sử dụng (`functionType`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `functionType` (`WALK_IN`, `MONTHLY`, `BACKUP`) là cơ sở cốt lõi
 *   cho thuật toán điều phối xe tự động (`ZoneRoutingService`) và logic tìm chỗ trống.
 * - Minh chứng 2: Trạng thái `status` hỗ trợ xóa mềm (`ACTIVE` vs `DELETED`), bảo toàn
 *   lịch sử đỗ xe mà không làm hỏng khóa ngoại ở bảng `parking_sessions`.
 * =========================================================================================
 */
@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Mã định danh duy nhất của khu vực đỗ xe

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor; // Tầng chứa khu vực này

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType; // Loại phương tiện cho phép đỗ (Ô tô, Xe máy...)

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName; // Tên hiển thị của khu vực (ví dụ: "Khu A - Khách vãng lai")

    @Column(name = "function_type", nullable = false, length = 50)
    private String functionType; // Loại chức năng chuyên dụng: WALK_IN (Vãng lai), MONTHLY (Tháng), BACKUP (Dự phòng)

    @Column(name = "layout_x")
    private Double layoutX; // Tọa độ X gốc của khu vực trên sơ đồ Konva SVG

    @Column(name = "layout_y")
    private Double layoutY; // Tọa độ Y gốc của khu vực trên sơ đồ Konva SVG

    private Integer rotation; // Góc quay (độ) của toàn bộ khu vực trên bản đồ

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // Trạng thái: ACTIVE (Hoạt động), DELETED (Xóa mềm)
}

