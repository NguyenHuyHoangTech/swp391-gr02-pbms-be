/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Repository for the Vehicle entity.
 *               Supports vehicle lookup by plate number, used during
 *               reservation creation and gate check-in flows.
 * @Dependencies:
 *  - Vehicle (com.pbms.modules.operation.domain)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPlateNumber(String plateNumber);
}
