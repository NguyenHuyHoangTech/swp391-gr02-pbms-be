/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-06
 * @Description: Repository for managing Monthly Tickets in the database.
 * @Dependencies: 
 * - MonthlyTicket (Entity)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.MonthlyTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface MonthlyTicketRepository extends JpaRepository<MonthlyTicket, Long> {

    Optional<MonthlyTicket> findByPlateAndStatus(String plate, String status);

    Optional<MonthlyTicket> findByRfidCard_CardCodeAndStatus(String cardCode, String status);

    Optional<MonthlyTicket> findTopByPlateAndStatusOrderByUpdatedAtDesc(String plate, String status);
    
    Optional<MonthlyTicket> findTopByRfidCard_CardCodeAndStatusOrderByUpdatedAtDesc(String cardCode, String status);

    long countByStatus(String status);

    @Query("SELECT COUNT(m) FROM MonthlyTicket m JOIN ParkingSession p ON m.plate = p.plate WHERE m.status = 'ACTIVE' AND p.status = 'ACTIVE' AND m.vehicleType.id = :vehicleTypeId")
    long countActiveMonthlyTicketsInsideByVehicleType(@Param("vehicleTypeId") Long vehicleTypeId);

    @Modifying
    @Query("UPDATE MonthlyTicket m SET m.status = 'EXPIRED', m.updatedAt = :now WHERE m.status = 'ACTIVE' AND m.validUntil < :now")
    int expirePastTickets(@Param("now") LocalDateTime now);
}
