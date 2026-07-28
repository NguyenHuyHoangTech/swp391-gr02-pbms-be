package com.pbms.modules.incident.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * =========================================================================================
 * THỰC THỂ LƯU TRỮ SỰ CỐ (INCIDENT TICKET ENTITY)
 * =========================================================================================
 * MỤC ĐÍCH:
 * Đại diện cho một bảng ghi (Record) trong Database lưu trữ thông tin về một sự
 * cố
 * xảy ra trong quá trình đỗ xe (mất thẻ, hỏng thẻ, đỗ sai bãi, xe trong danh
 * sách đen).
 * 
 * VÒNG ĐỜI CỦA MỘT TICKET (STATUS LIFECYCLE):
 * 1. PENDING (Chờ xử lý):
 * - Khách hàng hoặc hệ thống vừa tạo sự cố.
 * - Đang chờ nhân viên bảo vệ xác nhận ở Giai đoạn 1.
 * 2. WAITING_CHECKOUT (Chờ xe ra bãi):
 * - Nhân viên đã xác nhận sự cố, chốt mức phạt.
 * - Xe chưa thanh toán xong hoặc chưa đánh ra khỏi bãi.
 * 3. RESOLVED (Đã hoàn tất):
 * - Khách đã nộp phạt/thanh toán xong, xe đã ra khỏi bãi thành công.
 * 4. REJECTED / CANCELLED (Bị từ chối / Đã hủy):
 * - Sự cố báo cáo sai, bị khách tự hủy hoặc nhân viên bác bỏ.
 * 
 * KIẾN TRÚC LIÊN KẾT (RELATIONSHIPS):
 * - User (Khách hàng tạo sự cố)
 * - User (Nhân viên giải quyết sự cố)
 * - ParkingSession (Phiên đỗ xe gắn liền với sự cố này)
 * =========================================================================================
 */
@Entity
@Table(name = "incident_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentTicket extends BaseEntity {

    // -------------------------------------------------------------------------
    // 1. CÁC KHÓA NGOẠI (FOREIGN KEYS) VÀ LIÊN KẾT BẢNG
    // -------------------------------------------------------------------------

    /**
     * Người tạo sự cố (Khách hàng hoặc nhân viên tạo hộ).
     * Liên kết tới bảng Users.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Nhân viên chịu trách nhiệm xử lý và đóng sự cố này (Staff/Manager).
     * Sẽ được gán khi sự cố chuyển sang RESOLVED/REJECTED/CANCELLED.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private User staff;

    /**
     * Phiên đỗ xe (Parking Session) đang xảy ra sự cố.
     * Liên kết cực kỳ quan trọng để truy xuất biển số, loại xe và giờ vào bãi.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private com.pbms.modules.operation.domain.ParkingSession session;

    // -------------------------------------------------------------------------
    // 2. THÔNG TIN CƠ BẢN CỦA SỰ CỐ
    // -------------------------------------------------------------------------

    /**
     * Loại sự cố. Các giá trị thường dùng:
     * LOST_CARD, DAMAGED_CARD, ZONE_VIOLATION, BLACKLIST_VIOLATION, FEE_DISPUTE...
     */
    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    /**
     * Mức độ ưu tiên để hiển thị lên đầu danh sách: HIGH (Đỏ), MEDIUM (Vàng), LOW
     * (Xanh).
     */
    @Column(nullable = false, length = 50)
    private String priority; // HIGH, MEDIUM, LOW

    /**
     * Lời khai, mô tả ban đầu của người tạo sự cố (VD: "Tôi làm rơi thẻ ở hầm B1").
     */
    @Column(nullable = false, columnDefinition = "VARCHAR(MAX)")
    private String description;

    /**
     * Trạng thái hiện tại của Ticket (PENDING, WAITING_CHECKOUT, RESOLVED,
     * REJECTED, CANCELLED).
     */
    @Column(nullable = false, length = 50)
    private String status; // PENDING, WAITING_CHECKOUT, RESOLVED, REJECTED

    /**
     * Chuỗi danh sách URL ảnh minh chứng do khách hàng tải lên khi tạo sự cố (Ngăn
     * cách bởi dấu |).
     */
    @Column(name = "uploaded_doc_url", columnDefinition = "VARCHAR(MAX)")
    private String uploadedDocUrl;

    // -------------------------------------------------------------------------
    // 3. THÔNG TIN XỬ LÝ TỪ PHÍA NHÂN VIÊN (RESOLUTION DATA)
    // -------------------------------------------------------------------------

    /**
     * Ghi chú các bước xử lý của nhân viên (Thường dùng format [Phase 1]... [Phase
     * 2]...).
     */
    @Column(name = "resolution_notes", columnDefinition = "VARCHAR(MAX)")
    private String resolutionNotes;

    /**
     * Chuỗi danh sách URL ảnh minh chứng do nhân viên chụp lại khi giải quyết sự cố
     * (Thường có tiền tố [P1], [P2], [CX] để đánh dấu Giai đoạn).
     */
    @Column(name = "resolution_image_url", columnDefinition = "VARCHAR(MAX)")
    private String resolutionImageUrl;

    /**
     * Thời điểm sự cố chính thức đóng lại (Thành công, Hủy hoặc Bị từ chối).
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Số tiền phạt cuối cùng được chốt để yêu cầu khách hàng thanh toán đền bù.
     */
    @Column(name = "fine_amount")
    private java.math.BigDecimal fineAmount;

    /**
     * Phân loại lý do nếu sự cố bị Hủy (VD: GUEST_FOUND_CARD, INFO_INCORRECT).
     */
    @Column(name = "cancel_type", length = 50)
    private String cancelType;
}
