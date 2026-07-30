/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: Entity representing an Entry or Exit Gate of the parking building. 
 *               Mapped to the 'gates' table in the database.
 * @Dependencies: 
 * - Floor (com.pbms.modules.infrastructure.domain.Floor)
 * - VehicleType (com.pbms.modules.operation.domain.VehicleType)
 */  
package com.pbms.modules.infrastructure.domain;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN JPA VÀ LOMBOK
// =========================================================================
import jakarta.persistence.*;
import lombok.*;

/**
 * =========================================================================================
 * THỰC THỂ CỔNG KIỂM SOÁT BÃI XE (GATE DOMAIN ENTITY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Ánh xạ bảng `gates` trong cơ sở dữ liệu, đại diện cho một trạm/cổng kiểm soát
 * ra vào bãi xe thuộc một tầng (`Floor`). Chứa tọa độ hiển thị trên sơ đồ bãi
 * (`layoutX`, `layoutY`, `rotation`) và trạng thái hoạt động của cổng (`status`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Cột `status` dùng chung 1 bộ từ vựng với `GateConfigDTO`:
 *   `IDLE` (trống, chờ ca trực) / `OCCUPIED` (đang có nhân viên trực) /
 *   `MAINTENANCE` (bảo trì) / `DELETED` (đã xóa mềm). Chỉ `WorkSessionService`
 *   (mở/đóng ca) và luồng xóa mềm được phép ghi cột này — màn hình sơ đồ chỉ
 *   sửa tên/vị trí. Khi ĐỌC ra cho Frontend, cả `GateController` lẫn
 *   `MapConfigurationService` đều SUY RA trạng thái từ ca trực ACTIVE thật
 *   trong bảng `staff_work_sessions` nên không phụ thuộc cột này có lệch hay không.
 * - Minh chứng 2: Tham số `layoutX`, `layoutY`, `rotation` phục vụ trực tiếp bộ vẽ
 *   Konva SVG trên Frontend (`SpaceMapScreen.tsx`) để nhân viên giám sát quan sát cổng.
 * =========================================================================================
 */
@Entity
@Table(name = "gates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Mã định danh duy nhất của cổng kiểm soát

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor; // Tầng chứa cổng kiểm soát này

    @Column(name = "gate_name", nullable = false, length = 100)
    private String gateName; // Tên hiển thị của cổng (ví dụ: "Cổng Vào Vào 1", "Cổng Ra Chính")

    @Column(length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // Trạng thái vật lý: ACTIVE (Hoạt động), INACTIVE (Không hoạt động/đóng)

    @Column(name = "layout_x")
    private Double layoutX; // Tọa độ X của cổng trên bản đồ Konva SVG

    @Column(name = "layout_y")
    private Double layoutY; // Tọa độ Y của cổng trên bản đồ Konva SVG

    private Integer rotation; // Góc quay (độ) của icon cổng trên bản đồ
}

