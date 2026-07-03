package com.pbms.modules.operation.repository;

import com.pbms.modules.identity.domain.StaffWorkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffWorkSessionRepository extends JpaRepository<StaffWorkSession, Long> {
    List<StaffWorkSession> findByStaffIdAndLoginTimeBetween(Long staffId, LocalDateTime startTime, LocalDateTime endTime);
    Optional<StaffWorkSession> findByStaffIdAndStatus(Long staffId, String status);
    Optional<StaffWorkSession> findByGateIdAndStatus(Long gateId, String status);

    org.springframework.data.domain.Page<StaffWorkSession> findByStatusAndLogoutTimeBetween(String status, LocalDateTime start, LocalDateTime end, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<StaffWorkSession> findByStatus(String status, org.springframework.data.domain.Pageable pageable);
}

