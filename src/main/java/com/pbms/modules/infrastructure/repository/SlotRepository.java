package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    long countByZoneIdAndStatus(Long zoneId, String status);
    
    @Query("SELECT COUNT(s) FROM Slot s WHERE s.zone.functionType = :functionType AND s.zone.vehicleType.id = :vehicleTypeId AND s.status = :status")
    long countByFunctionTypeAndVehicleTypeIdAndStatus(@Param("functionType") String functionType, @Param("vehicleTypeId") Long vehicleTypeId, @Param("status") String status);

    long countByZoneId(Long zoneId);
    java.util.List<Slot> findByZoneId(Long zoneId);
    long countByZone_FunctionType(String functionType);
}

