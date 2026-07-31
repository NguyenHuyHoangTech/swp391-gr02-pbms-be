/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC TƯƠNG TÁC CƠ SỞ DỮ LIỆU (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHAI BÁO DATA ACCESS OBJECT (DAO)
 * - Minh chứng: Class kế thừa JpaRepository. Đây là cơ chế Spring Data JPA 
 *   giúp tự động sinh ra các câu lệnh SQL (CRUD) mà không cần viết code.
 * 
 * BƯỚC 2: ĐỊNH NGHĨA CÁC CÂU TRUY VẤN TÙY CHỈNH (CUSTOM QUERIES)
 * - Minh chứng: Các hàm truy vấn được Spring Data JPA 
 *   tự động dịch thành câu lệnh SELECT * FROM ... WHERE ... dựa theo tên hàm.
 * - Hoặc sử dụng @Query để viết trực tiếp câu lệnh JPQL/Native SQL.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
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

