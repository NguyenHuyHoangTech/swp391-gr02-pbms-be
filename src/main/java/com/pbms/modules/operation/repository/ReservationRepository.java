/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Repository for the Reservation entity.
 *               Provides data access methods for the pre-booking feature,
 *               including conflict detection and scheduler queries.
 * @Dependencies:
 *  - Reservation (com.pbms.modules.operation.domain)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByOrderByCreatedAtDesc();

    long countByZoneIdAndStatus(Long zoneId, String status);

    List<Reservation> findByVehicle_PlateNumberAndStatus(String plateNumber, String status);

    List<Reservation> findByStatus(String status);

    List<Reservation> findByZoneIdAndStatus(Long zoneId, String status);

    /**
     * @Function: findUpcomingReservations
     * @Description: Retrieves PENDING reservations whose expectedEntryTime falls
     *               within a given time window. Used by the conflict scheduler
     *               to detect and resolve zone capacity issues proactively.
     * @param status - reservation status to filter (typically "PENDING")
     * @param start  - beginning of the time window (usually: now)
     * @param end    - end of the time window (usually: now + RESERVATION_EARLY_MINS)
     */
    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.expectedEntryTime BETWEEN :start AND :end")
    List<Reservation> findUpcomingReservations(
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
