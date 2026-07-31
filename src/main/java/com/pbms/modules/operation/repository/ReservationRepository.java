/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Lớp Repository (Data Access Layer) thao tác với Database cho thực thể Reservation (Đơn đặt chỗ).
 *               Cung cấp các hàm tự động sinh câu lệnh SQL bằng Spring Data JPA.
 * @Dependencies: 
 * - JpaRepository (Kế thừa các hàm cơ bản như save, findById, delete)
 */
package com.pbms.modules.operation.repository;

import com.pbms.modules.operation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    
    /**
     * Lấy toàn bộ danh sách đơn đặt chỗ, sắp xếp theo thời gian tạo giảm dần (mới nhất lên đầu).
     * Ứng dụng: Dùng để hiển thị lịch sử đặt chỗ trên màn hình quản trị của Admin/Staff.
     */
    List<Reservation> findAllByOrderByCreatedAtDesc();
    
    /**
     * Tìm kiếm đơn đặt chỗ dựa vào biển số xe và trạng thái.
     * Ứng dụng: Dùng để kiểm tra xem một biển số cụ thể có đang có đơn đặt chỗ nào chưa hoàn thành (PENDING) hay không.
     */
    List<Reservation> findByVehicle_PlateNumberAndStatus(String plateNumber, String status);
    
    /**
     * Lấy danh sách tất cả các đơn đặt chỗ theo trạng thái.
     * Ứng dụng: Scheduler chạy ngầm dùng hàm này để lấy tất cả các đơn đang PENDING để quét thời gian trễ giờ/hết hạn.
     */
    List<Reservation> findByStatus(String status);
    
    /**
     * Tìm các đơn đặt chỗ có thời gian dự kiến vào bãi nằm trong một khoảng thời gian (window).
     * Logic mã giả:
     * 1. Nhận vào tham số trạng thái (VD: PENDING) và 2 mốc thời gian start, end.
     * 2. Truy vấn Database lấy ra các đơn có expectedEntryTime nằm giữa start và end.
     * 3. Trả về danh sách để gửi thông báo nhắc nhở khách hàng.
     */
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.expectedEntryTime BETWEEN :start AND :end")
    List<Reservation> findUpcomingReservations(
        @org.springframework.data.repository.query.Param("status") String status, 
        @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start, 
        @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end
    );
}
