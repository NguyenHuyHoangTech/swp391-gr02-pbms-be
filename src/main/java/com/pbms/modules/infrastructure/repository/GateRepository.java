/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Gate entity.
 *               Provides methods to find gates by floor.
 * @Dependencies: 
 * - Gate (com.pbms.modules.infrastructure.domain.Gate)
 */  
package com.pbms.modules.infrastructure.repository;

// =========================================================================
// PHẦN 1: CÁC DOMAIN ENTITY VÀ THƯ VIỆN SPRING DATA JPA
// =========================================================================
import com.pbms.modules.infrastructure.domain.Gate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU CỔNG KIỂM SOÁT (GATE REPOSITORY)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface chịu trách nhiệm thao tác đọc/ghi cơ sở dữ liệu đối với bảng `gates`.
 *
 * BẰNG CHỨNG KIẾN TRÚC:
 * - Minh chứng 1: Kế thừa `JpaRepository<Gate, Long>` hỗ trợ truy xuất thông tin
 *   cổng nhanh chóng khi Mở/Đóng ca trực (`WorkSessionService`) và khi điều phối xe.
 * =========================================================================================
 */
@Repository
public interface GateRepository extends JpaRepository<Gate, Long> {
}

