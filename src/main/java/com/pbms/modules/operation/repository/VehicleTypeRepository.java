/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Repository interface for VehicleType entity providing database access operations.
 * @Dependencies: com.pbms.modules.operation.domain.VehicleType, org.springframework.data.jpa.repository.JpaRepository, java.util.Optional
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {

    /**
     * Finds a VehicleType by its exact type name.
     */
    Optional<VehicleType> findByTypeName(String typeName);
}
