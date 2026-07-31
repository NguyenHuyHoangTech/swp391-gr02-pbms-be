/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: JPA Repository for the StaffWorkSession entity.
 *               Provides lookups for the currently active shift of a staff member
 *               or of a gate, plus paginated history queries for completed shifts.
 * @Dependencies:
 * - StaffWorkSession (com.pbms.modules.identity.domain.StaffWorkSession)
 */
package com.pbms.modules.operation.repository;

// =========================================================================
// PHẦN 1: ENTITY VÀ THƯ VIỆN SPRING DATA JPA
// Công dụng: Cho phép định nghĩa 1 "Kho dữ liệu" chỉ bằng Interface, Spring
// Boot sẽ tự động sinh code triển khai ở dưới nền để chạy câu lệnh SQL tương
// ứng — không cần tự tay viết JDBC/SQL thủ công.
// =========================================================================
import com.pbms.modules.identity.domain.StaffWorkSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// =========================================================================
// PHẦN 2: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * =========================================================================================
 * KHO TRUY VẤN DỮ LIỆU CHO BẢNG "staff_work_sessions"
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Interface này là "cánh cửa" duy nhất để đọc/ghi dữ liệu ca trực nhân viên.
 * Đây là Repository ĐƯỢC DÙNG CHUNG BỞI NHIỀU MODULE, không riêng module quản lý
 * ca trực — nên mọi hàm khai báo ở đây phải giữ nguyên chữ ký để không phá vỡ
 * các module khác đang gọi tới:
 * - `identity` (WorkSessionService): mở/đóng ca, tra lịch sử ca trực.
 * - `infrastructure` (GateController, MapConfigurationService): suy ra trạng thái
 *   OCCUPIED/IDLE của Cổng dựa trên việc cổng đó có ca ACTIVE hay không.
 * - `operation` (GateOperationService, IotHardwareController, MonthlyTicketService):
 *   xác định nhân viên nào đang trực để gắn giao dịch vào đúng ca.
 * - `incident` (IncidentService): truy vết ca trực khi lập phiếu sự cố.
 *
 * BẰNG CHỨNG CƠ CHẾ HOẠT ĐỘNG (SPRING DATA JPA "MA THUẬT"):
 * - Minh chứng 1: `@Repository` báo cho Spring Boot biết đây là tầng truy cập
 *   Database, để Spring bọc thêm cơ chế dịch lỗi SQL thành Exception chuẩn.
 * - Minh chứng 2: `extends JpaRepository<StaffWorkSession, Long>` — chỉ cần kế
 *   thừa là ĐÃ CÓ SẴN `findAll()`, `findById()`, `save()`, `deleteById()`...
 * - Minh chứng 3: Các hàm bên dưới không có phần thân `{ ... }` vì Spring Data
 *   JPA tự đọc TÊN HÀM để suy ra câu lệnh SQL (Derived Query Method).
 * =========================================================================================
 */
@Repository
public interface StaffWorkSessionRepository extends JpaRepository<StaffWorkSession, Long> {

    // Tìm ca trực đang mở của 1 nhân viên. Trả về Optional (tối đa 1 kết quả) vì
    // hệ thống áp ràng buộc: 1 nhân viên chỉ được trực 1 cổng tại 1 thời điểm.
    Optional<StaffWorkSession> findByStaffIdAndStatus(Long staffId, String status);

    // Tìm ca trực đang mở tại 1 cổng. Cũng là Optional vì 1 cổng chỉ nhận đúng
    // 1 nhân viên ACTIVE. Đây là hàm mà tầng infrastructure dùng để suy ra Cổng
    // đang OCCUPIED hay IDLE mà không cần đọc cột `status` thô của bảng gates.
    Optional<StaffWorkSession> findByGateIdAndStatus(Long gateId, String status);

    // Lịch sử ca trực theo trạng thái, có phân trang (dùng cho bảng lịch sử).
    Page<StaffWorkSession> findByStatus(String status, Pageable pageable);

    // Lịch sử ca trực theo trạng thái + lọc theo khoảng thời gian ĐÓNG ca.
    Page<StaffWorkSession> findByStatusAndLogoutTimeBetween(String status, LocalDateTime start, LocalDateTime end,
            Pageable pageable);

    // Lịch sử ca trực theo trạng thái + lọc theo vai trò cổng (ENTRY/EXIT/IN_OUT).
    Page<StaffWorkSession> findByStatusAndWorkGateType(String status, String workGateType, Pageable pageable);

    // Lịch sử ca trực lọc đồng thời cả khoảng thời gian đóng ca VÀ vai trò cổng.
    Page<StaffWorkSession> findByStatusAndLogoutTimeBetweenAndWorkGateType(String status, LocalDateTime start,
            LocalDateTime end, String workGateType, Pageable pageable);
}
