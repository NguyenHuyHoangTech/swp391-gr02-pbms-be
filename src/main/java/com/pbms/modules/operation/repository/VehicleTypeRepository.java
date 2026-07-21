/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-15
 * @Description: Repository interface for VehicleType entity providing database access operations.
 * @Dependencies: com.pbms.modules.operation.domain.VehicleType, org.springframework.data.jpa.repository.JpaRepository, java.util.Optional
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {

    // Tra loại xe theo đúng tên hiển thị (typeName là cột unique trong DB) -
    // dùng để kiểm tra trùng tên hoặc tra cứu nhanh theo tên thay vì id.
    Optional<VehicleType> findByTypeName(String typeName);
}
