/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Slot entity.
 *               Provides methods to count and find slots based on zone and status.
 * @Dependencies: 
 * - Slot (com.pbms.modules.infrastructure.domain.Slot)
 */  
package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    long countByZoneIdAndStatus(Long zoneId, String status);
    
    @Query("SELECT COUNT(s) FROM Slot s WHERE s.zone.functionType = :functionType AND s.zone.vehicleType.id = :vehicleTypeId AND s.status = :status")
    long countByFunctionTypeAndVehicleTypeIdAndStatus(@Param("functionType") String functionType, @Param("vehicleTypeId") Long vehicleTypeId, @Param("status") String status);

    @Query("SELECT COUNT(s) FROM Slot s WHERE s.zone.vehicleType.id = :vehicleTypeId")
    long countByVehicleTypeId(@Param("vehicleTypeId") Long vehicleTypeId);

    long countByZoneId(Long zoneId);

    List<Slot> findByZoneId(Long zoneId);

    long countByZone_FunctionType(String functionType);

    // Projection cho thống kê slot theo nhiều zone trong 1 lượt gọi DB, tránh
    // gọi lặp countByZoneId/countByZoneIdAndStatus cho từng zone.
    interface ZoneSlotStats {
        Long getZoneId();
        Long getTotal();
        Long getDisabled();
        Long getOccupied();
    }

    @Query("SELECT s.zone.id AS zoneId, COUNT(s) AS total, " +
           "SUM(CASE WHEN s.status = 'DISABLED' THEN 1 ELSE 0 END) AS disabled, " +
           "SUM(CASE WHEN s.status = 'OCCUPIED' THEN 1 ELSE 0 END) AS occupied " +
           "FROM Slot s WHERE s.zone.id IN :zoneIds GROUP BY s.zone.id")
    List<ZoneSlotStats> aggregateStatsByZoneIds(@Param("zoneIds") java.util.Collection<Long> zoneIds);
}
