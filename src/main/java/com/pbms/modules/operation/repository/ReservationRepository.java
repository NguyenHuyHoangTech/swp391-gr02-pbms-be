package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByOrderByCreatedAtDesc();
    long countByZoneIdAndStatus(Long zoneId, String status);
    List<Reservation> findByVehicle_PlateNumberAndStatus(String plateNumber, String status);
    List<Reservation> findByStatus(String status);
    List<Reservation> findByZoneIdAndStatus(Long zoneId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.expectedEntryTime BETWEEN :start AND :end")
    List<Reservation> findUpcomingReservations(@org.springframework.data.repository.query.Param("status") String status, @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start, @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);
}

