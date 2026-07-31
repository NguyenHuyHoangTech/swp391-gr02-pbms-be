/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-06
 * @Description: Lớp Repository thao tác với Database cho thực thể MonthlyTicket (Vé tháng).
 *               Cung cấp các câu lệnh truy vấn phức tạp (JPQL) để xử lý logic vé tháng.
 * @Dependencies: 
 * - JpaRepository (Spring Data JPA)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.MonthlyTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonthlyTicketRepository extends JpaRepository<MonthlyTicket, Long> {
    
    /**
     * Tìm kiếm vé tháng duy nhất dựa vào biển số và trạng thái.
     * Ứng dụng: Dùng để kiểm tra xe khi vào bãi xem có vé tháng hợp lệ (ACTIVE) hay không.
     */
    Optional<MonthlyTicket> findByPlateNumberAndStatus(String plate, String status);

    /**
     * Đếm tổng số lượng vé tháng theo trạng thái.
     * Ứng dụng: Dùng để kiểm tra xem số lượng vé tháng (ACTIVE) đã vượt ngưỡng cho phép (Threshold) hay chưa.
     */
    long countByStatus(String status);

    /**
     * Lấy danh sách các vé tháng đã quá hạn nhưng vẫn còn trạng thái ACTIVE.
     * Logic mã giả:
     * 1. Query kiểm tra m.status = 'ACTIVE' và m.validUntil < thời điểm hiện tại (:now).
     * 2. Trả về list để Cronjob lấy thông tin email gửi thông báo hết hạn cho khách.
     */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM MonthlyTicket m WHERE m.status = 'ACTIVE' AND m.validUntil < :now")
    java.util.List<MonthlyTicket> findTicketsToProcessExpiration(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /**
     * Cập nhật đồng loạt trạng thái các vé tháng đã hết hạn thành EXPIRED.
     * Logic mã giả:
     * 1. Dùng annotation @Modifying để báo cho JPA biết đây là lệnh UPDATE.
     * 2. Cập nhật m.status = 'EXPIRED' cho tất cả vé đang ACTIVE có thời hạn < hiện tại.
     * 3. Trả về số lượng vé đã được cập nhật thành công để log ra console.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE MonthlyTicket m SET m.status = 'EXPIRED', m.updatedAt = :now WHERE m.status = 'ACTIVE' AND m.validUntil < :now")
    int expirePastTickets(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
