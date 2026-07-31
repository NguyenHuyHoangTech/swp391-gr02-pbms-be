/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Slot entity.
 *               Provides methods to count and find slots based on zone and status.
 * @Dependencies: 
 * - Slot (com.pbms.modules.infrastructure.domain.Slot)
 */  
package com.pbms.modules.infrastructure.repository;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN SPRING DATA JPA / QUERY
// =========================================================================
import com.pbms.modules.infrastructure.domain.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU Ô ĐỖ XE (SLOT REPOSITORY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface chịu trách nhiệm thao tác đọc/ghi cơ sở dữ liệu đối với bảng `slots`.
 * Cung cấp các câu truy vấn phức tạp (JPQL) để thống kê số lượng ô trống, tổng số ô
 * đỗ theo khu vực (`Zone`), loại chức năng (`functionType`) và loại xe (`VehicleType`).
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Các hàm đếm (`countBy...`) phục vụ trực tiếp cho thuật toán tính toán
 *   tỷ lệ lấp đầy (`fillThresholdPct`) trong `ZoneRoutingService` khi điều phối xe tự động.
 * - Minh chứng 2: Tích hợp với API của `SlotController` và `ZoneService` để xuất dữ liệu
 *   sơ đồ mặt bằng cho màn hình `SpaceMapScreen.tsx`.
 * =========================================================================================
 */
@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

    // Đếm số lượng ô đỗ trong một Zone theo trạng thái cụ thể (ví dụ: AVAILABLE, OCCUPIED)
    long countByZoneIdAndStatus(Long zoneId, String status);
    
    // Truy vấn JPQL đếm số ô đỗ thỏa mãn đồng thời: chức năng Zone + loại phương tiện + trạng thái
    @Query("SELECT COUNT(s) FROM Slot s WHERE s.zone.functionType = :functionType AND s.zone.vehicleType.id = :vehicleTypeId AND s.status = :status")
    long countByFunctionTypeAndVehicleTypeIdAndStatus(@Param("functionType") String functionType, @Param("vehicleTypeId") Long vehicleTypeId, @Param("status") String status);

    // Truy vấn JPQL đếm tổng số ô đỗ thuộc về một loại phương tiện cụ thể (CAR hoặc MOTORBIKE)
    @Query("SELECT COUNT(s) FROM Slot s WHERE s.zone.vehicleType.id = :vehicleTypeId")
    long countByVehicleTypeId(@Param("vehicleTypeId") Long vehicleTypeId);

    // Đếm tổng số lượng ô đỗ trong một Zone bất kể trạng thái
    long countByZoneId(Long zoneId);

    // Lấy toàn bộ danh sách ô đỗ thuộc một Zone (dùng để vẽ sơ đồ từng khu vực)
    java.util.List<Slot> findByZoneId(Long zoneId);

    // Đếm số ô đỗ dựa trên loại chức năng của Zone (WALK_IN, MONTHLY, BACKUP)
    long countByZone_FunctionType(String functionType);
}

