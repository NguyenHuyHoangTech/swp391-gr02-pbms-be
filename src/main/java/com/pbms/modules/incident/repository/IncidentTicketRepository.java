/**
 * @author Phạm Anh Tuấn
 * @created 12/6/2026
 */
package com.pbms.modules.incident.repository;

import com.pbms.modules.incident.domain.IncidentTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * =========================================================================================
 * REPOSITORY QUẢN LÝ DỮ LIỆU SỰ CỐ (INCIDENT TICKET REPOSITORY)
 * =========================================================================================
 * MỤC ĐÍCH:
 * Interface cung cấp các phương thức giao tiếp với Database (bảng incident_tickets)
 * thông qua Spring Data JPA. Hỗ trợ CRUD và các câu query phức tạp phục vụ 
 * nghiệp vụ giải quyết sự cố đỗ xe.
 * 
 * =========================================================================================
 */
@Repository
public interface IncidentTicketRepository extends JpaRepository<IncidentTicket, Long> {

    /**
     * Tìm tất cả sự cố theo loại (Ví dụ: LOST_CARD, FEE_DISPUTE).
     */
    List<IncidentTicket> findByIssueType(String issueType);

    /**
     * Tìm tất cả sự cố theo loại và theo trạng thái (Ví dụ: LOST_CARD đang PENDING).
     */
    List<IncidentTicket> findByIssueTypeAndStatus(String issueType, String status);

    /**
     * Lấy toàn bộ sự cố trong hệ thống, sắp xếp mới nhất lên đầu.
     * Thường dùng cho Manager hoặc Staff để xem danh sách chờ xử lý.
     */
    List<IncidentTicket> findAllByOrderByIdDesc();

    /**
     * Tìm toàn bộ sự cố do một User cụ thể tạo ra, sắp xếp mới nhất lên đầu.
     */
    List<IncidentTicket> findByUserEmailOrderByIdDesc(String email);
    
    /**
     * Lấy danh sách sự cố của một khách hàng dựa trên 2 điều kiện:
     * 1. Sự cố do chính email của họ báo cáo.
     * 2. HOẶC sự cố gắn với một phiên đỗ xe mà biển số xe thuộc quyền sở hữu của email đó.
     * Mục đích: Cho phép khách hàng xem được sự cố của xe mình dù nhân viên là người tạo hộ.
     */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT i FROM IncidentTicket i LEFT JOIN i.session s " +
           "WHERE i.user.email = :email " +
           "OR EXISTS (SELECT 1 FROM Vehicle v WHERE v.user.email = :email AND v.plateNumber = s.plate AND v.vehicleType = s.vehicleType) " +
           "ORDER BY i.id DESC")
    List<IncidentTicket> findAllByUserEmailOrVehicleOwner(@org.springframework.data.repository.query.Param("email") String email);

    /**
     * Lấy danh sách các sự cố phát sinh trong một phiên đỗ xe cụ thể.
     */
    List<IncidentTicket> findBySessionId(Long sessionId);

    /**
     * Lọc các sự cố đã được xử lý xong bởi một nhân viên cụ thể trong khoảng thời gian.
     * Dùng cho nghiệp vụ tính KPI hoặc thống kê báo cáo cuối ngày của bảo vệ.
     */
    List<IncidentTicket> findByUserIdAndResolvedAtBetweenAndStatus(Long userId, java.time.LocalDateTime start, java.time.LocalDateTime end, String status);

    /**
     * Kiểm tra xem phiên đỗ xe này có sự cố nào cùng loại mà đang KHÔNG ở trạng thái cụ thể hay không.
     */
    boolean existsBySessionIdAndIssueTypeAndStatusNot(Long sessionId, String issueType, String status);
    
    /**
     * Kiểm tra xem phiên đỗ xe có sự cố thuộc một trong các trạng thái đang chờ xử lý hay không.
     * Dùng để chặn tạo trùng ticket (Ví dụ: Đang có ticket LOST_CARD PENDING thì không cho tạo thêm).
     */
    boolean existsBySessionIdAndIssueTypeAndStatusIn(Long sessionId, String issueType, java.util.List<String> statuses);

    /**
     * Tương tự hàm trên nhưng check nhiều loại sự cố cùng lúc.
     */
    boolean existsBySessionIdAndIssueTypeInAndStatusIn(Long sessionId, java.util.List<String> issueTypes, java.util.List<String> statuses);

    /**
     * Kiểm tra xem phiên đỗ xe đã từng có loại sự cố này chưa (Bất kể trạng thái nào).
     */
    boolean existsBySessionIdAndIssueType(Long sessionId, String issueType);

    /**
     * Tìm các sự cố theo biển số xe, loại sự cố và trạng thái. 
     * Hữu ích khi hệ thống nhận diện biển số (LPR) báo lỗi và cần tra cứu nhanh.
     */
    @org.springframework.data.jpa.repository.Query("SELECT i FROM IncidentTicket i JOIN i.session s WHERE s.plate = :plate AND i.issueType = :issueType AND i.status = :status")
    List<IncidentTicket> findBySessionPlateAndIssueTypeAndStatus(@org.springframework.data.repository.query.Param("plate") String plate, @org.springframework.data.repository.query.Param("issueType") String issueType, @org.springframework.data.repository.query.Param("status") String status);

    /**
     * Tìm sự cố của một xe cụ thể (theo biển số + loại xe) đang ở một trạng thái nhất định.
     */
    @org.springframework.data.jpa.repository.Query("SELECT i FROM IncidentTicket i JOIN i.session s WHERE s.plate = :plate AND s.vehicleType.id = :vehicleTypeId AND i.status = :status")
    List<IncidentTicket> findBySessionPlateAndVehicleTypeIdAndStatus(
            @org.springframework.data.repository.query.Param("plate") String plate,
            @org.springframework.data.repository.query.Param("vehicleTypeId") Long vehicleTypeId,
            @org.springframework.data.repository.query.Param("status") String status);

    /**
     * Đếm tổng số sự cố chưa giải quyết của một loại phương tiện cụ thể.
     * Dùng cho Dashboard quản lý để hiển thị cảnh báo (Ví dụ: Có bao nhiêu xe máy đang mất thẻ).
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(i) FROM IncidentTicket i JOIN i.session s " +
            "WHERE i.issueType = :issueType AND i.status != :status " +
            "AND s.vehicleType.id = :vehicleTypeId AND s.status IN ('ACTIVE', 'LOCKED')")
    long countUnresolvedByIssueTypeAndVehicleTypeId(
            @org.springframework.data.repository.query.Param("issueType") String issueType,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("vehicleTypeId") Long vehicleTypeId);
}
