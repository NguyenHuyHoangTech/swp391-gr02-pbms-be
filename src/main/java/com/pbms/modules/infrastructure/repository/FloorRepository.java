/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Floor entity.
 *               Provides standard CRUD operations for parking floors.
 * @Dependencies: 
 * - Floor (com.pbms.modules.infrastructure.domain.Floor)
 */  
package com.pbms.modules.infrastructure.repository;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN SPRING DATA JPA
// =========================================================================
import com.pbms.modules.infrastructure.domain.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU TẦNG BÃI XE (FLOOR REPOSITORY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface chịu trách nhiệm thao tác đọc/ghi cơ sở dữ liệu đối với bảng `floors`.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Kế thừa `JpaRepository<Floor, Long>` giúp tự động cung cấp các hàm
 *   truy vấn cơ bản (`findAll`, `findById`, `save`, `delete`) mà không cần viết SQL thủ công.
 * =========================================================================================
 */
@Repository
public interface FloorRepository extends JpaRepository<Floor, Long> {
}

