/**
<<<<<<< HEAD
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-15
 * @Description: Repository interface for VehicleType entity providing database access operations.
 * @Dependencies: com.pbms.modules.operation.domain.VehicleType, org.springframework.data.jpa.repository.JpaRepository, java.util.Optional
=======
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Repository for the VehicleType entity.
 *               Used during reservation preview price calculation
 *               to retrieve the vehicle type's pricing category.
 * @Dependencies:
 *  - VehicleType (com.pbms.modules.operation.domain)
>>>>>>> origin/develop
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
<<<<<<< HEAD
import java.util.Optional;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {
    Optional<VehicleType> findByTypeName(String typeName);
}

=======
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {

    Optional<VehicleType> findByTypeName(String typeName);
}
>>>>>>> origin/develop
