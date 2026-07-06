package com.pbms.modules.incident.repository;

import com.pbms.modules.incident.domain.IncidentTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentTicketRepository extends JpaRepository<IncidentTicket, Long> {
    List<IncidentTicket> findByIssueType(String issueType);
    List<IncidentTicket> findByIssueTypeAndStatus(String issueType, String status);
    List<IncidentTicket> findAllByOrderByIdDesc();
    List<IncidentTicket> findByUserEmailOrderByIdDesc(String email);
    List<IncidentTicket> findBySessionId(Long sessionId);
    List<IncidentTicket> findByUserIdAndResolvedAtBetweenAndStatus(Long userId, java.time.LocalDateTime start, java.time.LocalDateTime end, String status);
    boolean existsBySessionIdAndIssueTypeAndStatusNot(Long sessionId, String issueType, String status);
    boolean existsByReportedPlateAndIssueTypeAndStatusNot(String reportedPlate, String issueType, String status);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(i) FROM IncidentTicket i JOIN i.session s " +
            "WHERE i.issueType = :issueType AND i.status != :status " +
            "AND s.vehicleType.id = :vehicleTypeId AND s.status IN ('ACTIVE', 'LOCKED')")
    long countUnresolvedByIssueTypeAndVehicleTypeId(
            @org.springframework.data.repository.query.Param("issueType") String issueType,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("vehicleTypeId") Long vehicleTypeId);
}

