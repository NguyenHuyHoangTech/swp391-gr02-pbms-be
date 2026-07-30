/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the RoutingRule entity.
 *               Provides methods to find active routing rules by zone.
 * @Dependencies: 
 * - RoutingRule (com.pbms.modules.infrastructure.domain.RoutingRule)
 */  
package com.pbms.modules.infrastructure.repository;

// =========================================================================
// PHẦN 1: ENTITY VÀ THƯ VIỆN SPRING DATA JPA
// Công dụng: Cho phép định nghĩa 1 "Kho dữ liệu" chỉ bằng Interface, Spring
// Boot sẽ tự động sinh code triển khai (implementation) ở dưới nền để chạy
// câu lệnh SQL tương ứng — không cần tự tay viết JDBC/SQL thủ công.
// =========================================================================
import com.pbms.modules.infrastructure.domain.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU CHO BẢNG "routing_rules"
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface này là "cánh cửa" duy nhất để đọc/ghi dữ liệu bảng `routing_rules`
 * từ tầng Service (RoutingRuleService, ZoneRoutingService). Không có class nào
 * khác được phép tự viết SQL trực tiếp vào bảng này ngoài Repository này.
 *
 * BẰNG CHỨNG CƠ CHẾ HOẠT ĐỘNG (SPRING DATA JPA "MA THUẬT"):
 * - Minh chứng 1: `@Repository` báo cho Spring Boot biết đây là tầng truy cập
 *   Database, để Spring bọc thêm cơ chế tự động dịch lỗi SQL thành Exception
 *   chuẩn của Spring (dễ bắt lỗi hơn).
 * - Minh chứng 2: `extends JpaRepository<RoutingRule, Long>` — chỉ cần kế thừa
 *   là ĐÃ CÓ SẴN không cần viết thêm 1 dòng code nào: `findAll()`, `findById()`,
 *   `save()`, `deleteById()`... (tham số `RoutingRule` là kiểu Entity quản lý,
 *   `Long` là kiểu dữ liệu của khóa chính).
 * - Minh chứng 3: Hàm bên dưới không có phần thân `{ ... }` vì Spring Data JPA
 *   tự đọc TÊN HÀM để "đoán" ra câu lệnh SQL cần chạy (Derived Query Method).
 * =========================================================================================
 */
@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
}
