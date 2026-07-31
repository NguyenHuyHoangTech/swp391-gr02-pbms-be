// Author: Võ Trung Hiếu
package com.pbms.modules.system.repository;

import com.pbms.modules.system.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resource IS NULL OR LOWER(a.resource) LIKE LOWER(CONCAT('%', :resource, '%'))) AND " +
           "(:email IS NULL OR LOWER(a.actor.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate)")
    Page<AuditLog> findWithFilters(
            @Param("action") String action,
            @Param("resource") String resource,
            @Param("email") String email,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}

