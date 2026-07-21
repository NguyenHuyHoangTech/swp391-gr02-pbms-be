/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-15
 * @Description: Entity class representing the vehicle_types table in the database.
 * @Dependencies: jakarta.persistence.*, lombok.*
 */
package com.pbms.modules.operation.domain;

import jakarta.persistence.*;
import lombok.*;

// Entity "loại xe" (bảng vehicle_types). Quyết định kích thước ô lưới của
// mỗi chỗ đỗ trên bản đồ (matrixWidth x matrixHeight, đơn vị số ô lưới) và
// là khoá để gắn chính sách giá + phân chia zone theo loại xe.
@Entity
@Table(name = "vehicle_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tên hiển thị, DUY NHẤT (unique) - không cho 2 loại xe trùng tên.
    @Column(name = "type_name", nullable = false, unique = true, length = 100)
    private String typeName;

    // Chiều rộng 1 chỗ đỗ tính theo số ô lưới (FE nhân với GRID_SIZE ra px).
    @Column(name = "matrix_width", nullable = false)
    private Integer matrixWidth;

    // Chiều cao 1 chỗ đỗ tính theo số ô lưới.
    @Column(name = "matrix_height", nullable = false)
    private Integer matrixHeight;

    // Hạng mục xe, quyết định loại tầng chứa được: FOUR_WHEEL (ô tô) hoặc
    // TWO_WHEEL (xe máy). Tầng chỉ nhận zone có loại xe cùng hạng mục.
    @Column(name = "category", length = 50)
    private String category;

    // Trạng thái ACTIVE/INACTIVE (khoá mềm - xem VehicleTypeService).
    @Column(name = "status", length = 20)
    private String status;

    // Đường dẫn icon hiển thị trên bản đồ/danh sách; có thể để trống.
    @Column(name = "icon_url", length = 255)
    private String iconUrl;
}
