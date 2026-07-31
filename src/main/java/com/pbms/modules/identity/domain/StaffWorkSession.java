/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Entity representing a staff duty shift at a control gate.
 *               Mapped to the 'staff_work_sessions' table in the database.
 * @Dependencies:
 * - User (com.pbms.modules.identity.domain.User)
 * - Gate (com.pbms.modules.infrastructure.domain.Gate)
 */
package com.pbms.modules.identity.domain;

// =========================================================================
// PHẦN 1: ENTITY LIÊN KẾT (KHÓA NGOẠI)
// Công dụng: 1 ca trực luôn gắn với ĐÚNG 1 nhân viên và ĐÚNG 1 cổng vật lý.
// =========================================================================
import com.pbms.modules.infrastructure.domain.Gate;

// =========================================================================
// PHẦN 2: THƯ VIỆN JPA (JAKARTA PERSISTENCE API)
// Công dụng: Bộ annotation giúp Hibernate biến class Java này thành 1 bảng
// thật sự trong Database (ánh xạ Object <-> Table, còn gọi là ORM).
// =========================================================================
import jakarta.persistence.*;

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK
// Công dụng: Tự sinh Getter/Setter/Constructor/Builder lúc biên dịch.
// =========================================================================
import lombok.*;

// =========================================================================
// PHẦN 4: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * =========================================================================================
 * KHUÔN MẪU DỮ LIỆU CHO BẢNG "staff_work_sessions" (CA TRỰC CỦA NHÂN VIÊN TẠI CỔNG)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Mỗi dòng trong bảng này là 1 CA LÀM VIỆC của nhân viên kiểm soát tại 1 cổng:
 * mở ca lúc nào, đóng ca lúc nào, trực cổng theo vai trò gì, và số doanh thu
 * hệ thống chốt được trong ca đó để đối soát với tiền mặt nhân viên nộp lại.
 *
 * VÌ SAO VAI TRÒ VÀO/RA NẰM Ở ĐÂY MÀ KHÔNG NẰM Ở BẢNG `gates`:
 * Cổng là chốt vật lý TRUNG TÍNH — nó không mang sẵn vai trò vào hay ra. Vai trò
 * đó do nhân viên khai báo lúc mở ca và được lưu ở `workGateType` của chính ca
 * trực này. Nhờ vậy cùng 1 chốt có thể làm cổng VÀO buổi sáng cao điểm rồi làm
 * cổng RA buổi chiều, không cần sửa lại cấu hình hạ tầng.
 *
 * BẰNG CHỨNG ÁNH XẠ ORM (OBJECT-RELATIONAL MAPPING):
 * - Minh chứng 1: `@Entity` + `@Table(name = "staff_work_sessions")` chỉ đích danh
 *   bảng vật lý trong Database mà class này ánh xạ tới.
 * - Minh chứng 2: `@Id` + `@GeneratedValue(IDENTITY)` đánh dấu khóa chính, giao
 *   cho Database tự tăng số mỗi khi mở 1 ca trực mới.
 * - Minh chứng 3: 2 quan hệ `@ManyToOne` (`staff`, `gate`) là bằng chứng khóa
 *   ngoại N-1: nhiều ca trực có thể thuộc về cùng 1 nhân viên, và nhiều ca trực
 *   (khác thời điểm) có thể diễn ra tại cùng 1 cổng.
 * - Minh chứng 4: `fetch = FetchType.LAZY` ở cả 2 quan hệ — chỉ tải dữ liệu
 *   nhân viên/cổng lên RAM khi thật sự gọi `.getStaff()`/`.getGate()`, tránh tốn
 *   tài nguyên khi chỉ cần đọc thời gian mở/đóng ca.
 * =========================================================================================
 */
@Entity
@Table(name = "staff_work_sessions")
@Getter // Lombok tự sinh toàn bộ hàm getXxx() cho mọi field bên dưới
@Setter // Lombok tự sinh toàn bộ hàm setXxx() cho mọi field bên dưới
@NoArgsConstructor // Constructor rỗng — Hibernate bắt buộc cần để khởi tạo Object trước khi nhồi dữ liệu
@AllArgsConstructor // Constructor đầy đủ tham số
@Builder // Cho phép khởi tạo kiểu chuỗi: StaffWorkSession.builder().staff(...).build()
public class StaffWorkSession {

    // Khóa chính của bảng, Database tự tăng số mỗi khi mở 1 ca trực mới.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nhân viên đang trực ca này. Bắt buộc có (nullable = false) vì không thể
    // tồn tại 1 ca trực mà không biết ai trực.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    // Cổng vật lý mà ca trực này diễn ra. Bắt buộc có.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", nullable = false)
    private Gate gate;

    // Thời điểm nhân viên mở ca (bấm "Bắt đầu ca trực" trên giao diện).
    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    // Thời điểm nhân viên đóng ca. Còn NULL nghĩa là ca vẫn đang diễn ra.
    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    // Trạng thái ca trực: ACTIVE (đang trực) hoặc COMPLETED (đã chốt ca).
    // Toàn hệ thống dựa vào giá trị ACTIVE của cột này để biết cổng nào đang có
    // người trực (xem `GateController.toDTO`), thay vì lưu 1 cờ tĩnh trên Gate.
    @Column(nullable = false, length = 50)
    private String status;

    // Tổng doanh thu hệ thống tính được trong ca (tiền mặt + các hình thức khác).
    // Chỉ được ghi 1 lần duy nhất tại thời điểm đóng ca, dùng để đối soát.
    @Column(name = "expected_revenue")
    private BigDecimal expectedRevenue;

    // Phần doanh thu thu bằng TIỀN MẶT trong ca — con số nhân viên phải nộp lại.
    @Column(name = "expected_cash_revenue")
    private BigDecimal expectedCashRevenue;

    // Phần doanh thu thu bằng hình thức KHÁC tiền mặt (chuyển khoản, ví điện tử).
    @Column(name = "expected_other_revenue")
    private BigDecimal expectedOtherRevenue;

    // Vai trò cổng của ca trực này: ENTRY (chỉ cổng vào), EXIT (chỉ cổng ra),
    // IN_OUT (trực cả 2 chiều — cũng là giá trị mặc định khi không khai báo).
    // Quyết định cách đếm số lượt giao dịch khi chốt ca (xem `WorkSessionService`).
    @Column(name = "work_gate_type", length = 50)
    private String workGateType;
}
