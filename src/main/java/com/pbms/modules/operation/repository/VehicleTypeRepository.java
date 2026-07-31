/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Repository for the VehicleType entity.
 *               Used during reservation preview price calculation
 *               to retrieve the vehicle type's pricing category.
 * @Dependencies:
 *  - VehicleType (com.pbms.modules.operation.domain)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {

    Optional<VehicleType> findByTypeName(String typeName);
}
