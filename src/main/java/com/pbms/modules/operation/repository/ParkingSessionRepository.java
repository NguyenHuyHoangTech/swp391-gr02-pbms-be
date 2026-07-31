package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.ParkingSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {
    
    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.status = 'ACTIVE' AND ps.vehicleType.id = :vehicleTypeId " +
           "AND ps.plate IN (SELECT mt.plateNumber FROM MonthlyTicket mt WHERE mt.status = 'ACTIVE' AND mt.validUntil > :currentTime)")
    long countActiveMonthlyCarsByVehicleType(@Param("vehicleTypeId") Long vehicleTypeId, @Param("currentTime") LocalDateTime currentTime);
    
    long countByVehicleTypeIdAndStatus(Long vehicleTypeId, String status);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSession> findByRfidCard_CardIdAndStatus(String cardId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSession> findByRfidCard_CardCodeAndStatus(String cardCode, String status);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSession> findByRfidCard_CardCodeAndStatusIn(String cardCode, List<String> statuses);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSession> findByPlateAndStatus(String plate, String status);
    
    List<ParkingSession> findByPlateAndVehicleTypeIdAndStatus(String plate, Long vehicleTypeId, String status);
    List<ParkingSession> findByPlateAndVehicleTypeIdAndStatusIn(String plate, Long vehicleTypeId, List<String> statuses);

    List<ParkingSession> findByStatus(String status);
    
    @Query("SELECT ps FROM ParkingSession ps WHERE ps.status = 'ACTIVE' AND ps.timeIn < :cutoffTime " +
           "AND ps.plate NOT IN (SELECT mt.plateNumber FROM MonthlyTicket mt WHERE mt.status = 'ACTIVE')")
    List<ParkingSession> findActiveSessionsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    List<ParkingSession> findByPlateOrderByTimeInDesc(String plate);
    List<ParkingSession> findByPlateContainingIgnoreCaseOrderByTimeInDesc(String plate);
    List<ParkingSession> findByGateInIdAndTimeInBetween(Long gateId, LocalDateTime start, LocalDateTime end);
    List<ParkingSession> findByGateOutIdAndTimeOutBetween(Long gateId, LocalDateTime start, LocalDateTime end);
    boolean existsByPlateAndTimeInGreaterThanEqual(String plate, LocalDateTime timeIn);
    Optional<ParkingSession> findTopByReservationIdOrderByTimeInDesc(Long reservationId);

    Page<ParkingSession> findByTimeInBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<ParkingSession> findByTimeInBetweenAndStatus(LocalDateTime start, LocalDateTime end, String status, Pageable pageable);
    Page<ParkingSession> findByStatus(String status, Pageable pageable);

    @QueryHints(value = { @QueryHint(name = "org.hibernate.readOnly", value = "true"),
                          @QueryHint(name = "org.hibernate.fetchSize", value = "500"),
                          @QueryHint(name = "org.hibernate.cacheable", value = "false") })
    Stream<ParkingSession> readByTimeInBetweenOrderByTimeInDesc(LocalDateTime start, LocalDateTime end);
}

