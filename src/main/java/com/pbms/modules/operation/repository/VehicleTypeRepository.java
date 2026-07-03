package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {
    Optional<VehicleType> findByTypeName(String typeName);
}

