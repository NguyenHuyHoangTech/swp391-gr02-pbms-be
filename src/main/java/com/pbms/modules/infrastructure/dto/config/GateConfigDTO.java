/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Gate Configuration.
 * @Dependencies: lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor
 */
package com.pbms.modules.infrastructure.dto.config;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK
// =========================================================================
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU CẤU HÌNH CỔNG KIỂM SOÁT (GATE CONFIG DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng DTO mô tả trạng thái cấu hình của một Cổng kiểm soát trên bản đồ bãi xe,
 * bao gồm thông tin nhân viên trực ban hiện tại (`staffName`, `staffEmail`) và tọa
 * độ hiển thị trên sơ đồ.
 *
 * VÌ SAO KHÔNG CÓ TRƯỜNG "LOẠI CỔNG" (IN/OUT):
 * Cổng là một chốt vật lý TRUNG TÍNH — nó không mang sẵn vai trò vào hay ra. Vai
 * trò đó thuộc về TỪNG CA TRỰC (`StaffWorkSession.workGateType` = ENTRY/EXIT), do
 * nhân viên khai báo lúc mở ca. Nhờ vậy cùng một chốt có thể làm cổng VÀO buổi
 * sáng cao điểm rồi làm cổng RA buổi chiều, không phải sửa lại hạ tầng.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Trường `floor` lưu chuỗi tên tầng (như "B1", "B2") giúp Frontend
 *   dễ dàng ánh xạ và lọc các cổng theo tầng đang chọn.
 * - Minh chứng 2: Trạng thái `status` (`IDLE`, `OCCUPIED`, `MAINTENANCE`) được
 *   tổng hợp động để giao diện quản lý cổng (`GateConsoleScreen.tsx`) hiển thị.
 * =========================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateConfigDTO {
    private Long id;          // Mã định danh cổng
    private Long floorId;     // Mã định danh tầng chứa cổng
    private String floor;     // Tên viết tắt/hiển thị của tầng ("B1", "B2" cho Frontend mapping)
    private String staffName; // Tên nhân viên đang trực ca (nếu cổng đang OCCUPIED)
    private String staffEmail;// Email nhân viên đang trực ca
    private String name;      // Tên cổng kiểm soát
    private String status;    // Trạng thái vận hành: IDLE (trống), OCCUPIED (có người trực), MAINTENANCE (bảo trì)
    private Double layoutX;   // Tọa độ X trên bản đồ Konva SVG
    private Double layoutY;   // Tọa độ Y trên bản đồ Konva SVG
    private Integer rotation; // Góc quay của icon cổng
}

