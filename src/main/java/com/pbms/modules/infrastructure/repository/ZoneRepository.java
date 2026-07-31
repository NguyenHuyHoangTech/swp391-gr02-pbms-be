/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Zone entity.
 *               Provides standard CRUD operations for parking zones.
 * @Dependencies: 
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */  
package com.pbms.modules.infrastructure.repository;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN SPRING DATA JPA
// =========================================================================
import com.pbms.modules.infrastructure.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU KHU VỰC BÃI XE (ZONE REPOSITORY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface chịu trách nhiệm thao tác đọc/ghi cơ sở dữ liệu đối với bảng `zones`.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Kế thừa `JpaRepository<Zone, Long>` cung cấp các hàm truy xuất nhanh
 *   danh sách các Zone trên bản đồ và phục vụ thuật toán định tuyến (`ZoneRoutingService`).
 * =========================================================================================
 */
@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
}

