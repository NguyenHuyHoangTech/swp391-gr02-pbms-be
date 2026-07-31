/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Map Configuration.
 * @Dependencies: lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor, java.util.List
 */
package com.pbms.modules.infrastructure.dto.config;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN LOMBOK VÀ JAVA CHUẨN
// =========================================================================
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU TỔNG THỂ BẢN ĐỒ BÃI XE (MAP CONFIG DTO)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Đối tượng DTO tổng hợp toàn bộ dữ liệu cấu hình không gian bãi xe từ DB,
 * bao gồm danh sách các Tầng (`floors`), Khu vực (`zones`), Cổng kiểm soát (`gates`)
 * và Loại phương tiện (`vehicleTypes`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Là payload trả về duy nhất của endpoint `GET /api/v1/infrastructure/map-config`,
 *   giúp Frontend (`SpaceMapScreen.tsx`) khởi tạo toàn bộ sơ đồ 2D/3D trong một lần tải (Single Request).
 * =========================================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapConfigDTO {
    private List<FloorConfigDTO> floors;       // Danh sách các tầng của bãi xe
    private List<ZoneConfigDTO> zones;         // Danh sách toàn bộ khu vực đỗ xe
    private List<GateConfigDTO> gates;         // Danh sách các cổng kiểm soát ra/vào
    private List<VehicleTypeDTO> vehicleTypes; // Danh sách định danh loại phương tiện cho phép
}

