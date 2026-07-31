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

import com.pbms.modules.system.domain.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(String configKey);
}

