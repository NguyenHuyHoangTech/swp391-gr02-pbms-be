package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.ParkingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {
    
    @Query("SELECT COUNT(ps) FROM ParkingSession ps WHERE ps.status = 'ACTIVE' AND ps.vehicleType.id = :vehicleTypeId " +
           "AND ps.plate IN (SELECT mt.plate FROM MonthlyTicket mt WHERE mt.status = 'ACTIVE' AND mt.validUntil > CURRENT_TIMESTAMP)")
    long countActiveMonthlyCarsByVehicleType(@Param("vehicleTypeId") Long vehicleTypeId);
    
    long countBySuggestedZoneNameAndStatusAndSlotIsNull(String suggestedZoneName, String status);
    
    Optional<ParkingSession> findByRfidCard_CardCodeAndStatus(String cardCode, String status);
    Optional<ParkingSession> findByPlateAndStatus(String plate, String status);
    List<ParkingSession> findByStatus(String status);
    
    @Query("SELECT ps FROM ParkingSession ps WHERE ps.status = 'ACTIVE' AND ps.timeIn < :cutoffTime " +
           "AND ps.plate NOT IN (SELECT mt.plate FROM MonthlyTicket mt WHERE mt.status = 'ACTIVE')")
    List<ParkingSession> findActiveSessionsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    List<ParkingSession> findByPlateOrderByTimeInDesc(String plate);
    List<ParkingSession> findByGateInIdAndTimeInBetween(Long gateId, LocalDateTime start, LocalDateTime end);
    List<ParkingSession> findByGateOutIdAndTimeOutBetween(Long gateId, LocalDateTime start, LocalDateTime end);
    boolean existsByPlateAndTimeInGreaterThanEqual(String plate, LocalDateTime timeIn);
}

