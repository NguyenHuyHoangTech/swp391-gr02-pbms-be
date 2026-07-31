/**  
 * @Author: Nguyen Huu Thanh (TH)
 * @Date: 2026-07-02  
 * @Description: Entity representing a Floor in the parking building. 
 *               Mapped to the 'floors' table in the database.
 * @Dependencies: 
 * - BuildingProfile (com.pbms.modules.system.domain.BuildingProfile)
 */  
package com.pbms.modules.infrastructure.domain;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN JPA / LOMBOK
// =========================================================================
import com.pbms.modules.system.domain.BuildingProfile;
import jakarta.persistence.*;
import lombok.*;

/**
 * =========================================================================================
 * THỰC THỂ TẦNG HẦM / TẦNG GỬI XE (FLOOR DOMAIN ENTITY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Ánh xạ bảng `floors` trong cơ sở dữ liệu, lưu trữ thông tin không gian tầng bãi xe
 * thuộc một tòa nhà (`BuildingProfile`), bao gồm kích thước lưới sơ đồ (`mapCols`, `mapRows`)
 * và loại phương tiện đặc thù cho tầng (`floorType`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Mỗi tầng (`Floor`) chỉ có thể phục vụ loại phương tiện (`CAR` hoặc `MOTORBIKE`)
 *   nhất định theo `floorType` để đảm bảo xe 4 bánh không đi nhầm xuống hầm xe máy.
 * - Minh chứng 2: Tích hợp trực tiếp với bộ vẽ Konva SVG trên màn hình `SpaceMapScreen.tsx`
 *   thông qua các chỉ số lưới `mapCols` x `mapRows`.
 * =========================================================================================
 */
@Entity
@Table(name = "floors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Mã định danh duy nhất của tầng bãi xe

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingProfile building; // Tòa nhà chủ quản bãi xe

    @Column(name = "floor_name", nullable = false, length = 100)
    private String floorName; // Tên hiển thị tầng (ví dụ: "Tầng Hầm B1", "Tầng 1 - Nổi")

    @Column(name = "floor_type", length = 50)
    private String floorType; // Loại phương tiện chuyên dụng: CAR (Ô tô), MOTORBIKE (Xe máy)

    @Column(name = "map_cols")
    private Integer mapCols; // Số cột của lưới sơ đồ trên giao diện thiết kế (mặc định cho ma trận SVG)

    @Column(name = "map_rows")
    private Integer mapRows; // Số hàng của lưới sơ đồ trên giao diện thiết kế
}

