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
}

