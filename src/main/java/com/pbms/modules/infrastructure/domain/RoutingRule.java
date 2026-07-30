/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: Entity defining routing rules for vehicles within the parking building. 
 *               Mapped to the 'routing_rules' table in the database.
 * @Dependencies: 
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */  
package com.pbms.modules.infrastructure.domain;

// =========================================================================
// PHẦN 1: THƯ VIỆN JPA (JAKARTA PERSISTENCE API)
// Công dụng: Bộ annotation giúp Hibernate biến class Java này thành 1 bảng
// thật sự trong Database (ánh xạ Object <-> Table, còn gọi là ORM).
// =========================================================================
import jakarta.persistence.*;

// =========================================================================
// PHẦN 2: THƯ VIỆN LOMBOK
// Công dụng: Tự động sinh code lặp lại (Getter/Setter/Constructor/Builder)
// tại lúc biên dịch, giúp file gọn hơn, không phải gõ tay từng hàm getX()/setX().
// =========================================================================
import lombok.*;

/**
 * =========================================================================================
 * KHUÔN MẪU DỮ LIỆU CHO BẢNG "routing_rules" (LUẬT ĐIỀU PHỐI XE TỰ ĐỘNG)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Mỗi dòng (row) trong bảng này là 1 "luật" do quản lý cấu hình sẵn, quyết định:
 * "Khi Zone A đầy tới X%, trong khung giờ Y-Z, thì đẩy xe mới sang Zone B thay vì A".
 * Đây chính là dữ liệu cấu hình (Infrastructure) cho bộ máy điều phối chạy trong
 * lúc vận hành thực tế (xem ZoneRoutingService ở package operation).
 *
 * BẰNG CHỨNG ÁNH XẠ ORM (OBJECT-RELATIONAL MAPPING):
 * - Minh chứng 1: `@Entity` (dòng 26) báo cho Hibernate biết class này không phải
 *   Java thường mà là 1 khuôn Object đại diện cho 1 bảng SQL thật.
 * - Minh chứng 2: `@Table(name = "routing_rules")` (dòng 27) chỉ đích danh tên
 *   bảng vật lý trong Database mà class này ánh xạ tới.
 * - Minh chứng 3: `@Id` + `@GeneratedValue` (dòng 34-35) đánh dấu cột khóa chính
 *   (Primary Key), giao cho Database tự tăng số (IDENTITY) mỗi khi thêm dòng mới.
 * - Minh chứng 4: `@ManyToOne` (dòng 38, dòng 48) là bằng chứng của quan hệ
 *   Khóa ngoại (Foreign Key) N-1 tới bảng `zones` — nhiều luật có thể trỏ về
 *   1 Zone, nhưng đặc biệt ở đây có TỚI 2 khóa ngoại khác nhau cùng trỏ về
 *   `Zone` (xem giải thích field `zone` và `suggestedZone` bên dưới).
 * =========================================================================================
 */
@Entity
@Table(name = "routing_rules")
@Getter // Lombok tự sinh toàn bộ hàm getXxx() cho mọi field bên dưới
@Setter // Lombok tự sinh toàn bộ hàm setXxx() cho mọi field bên dưới
@NoArgsConstructor // Lombok tự sinh Constructor rỗng RoutingRule() — Hibernate bắt buộc cần cái này để khởi tạo Object trước khi nhồi dữ liệu vào
@AllArgsConstructor // Lombok tự sinh Constructor đầy đủ tham số (tiện cho test nhanh)
@Builder // Lombok tự sinh cách khởi tạo kiểu "chuỗi" RoutingRule.builder().ruleName("...").build()
public class RoutingRule {

    // Khóa chính (Primary Key) của bảng, Database tự tăng số mỗi khi thêm luật mới.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Khu vực (Zone) mà luật này áp dụng để GIÁM SÁT độ đầy — ví dụ "Zone A".
    // fetch = LAZY: chỉ tải dữ liệu Zone này lên RAM khi thật sự gọi tới .getZone(),
    // tránh tốn tài nguyên tải luôn cả Object Zone mỗi khi chỉ cần đọc RoutingRule.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    // Tên gợi nhớ của luật để quản lý dễ nhận diện trên giao diện cấu hình,
    // ví dụ: "Đẩy xe máy giờ cao điểm sáng".
    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    // Ngưỡng phần trăm lấp đầy (0-100) của `zone` để kích hoạt luật.
    // Ví dụ: 80 nghĩa là "khi Zone A đầy từ 80% trở lên thì bắt đầu điều phối".
    @Column(name = "fill_threshold_pct", nullable = false)
    private Integer fillThresholdPct;

    // Khu vực được ĐỀ XUẤT cho xe mới khi `zone` đã vượt ngưỡng ở trên, ví dụ "Zone B".
    // Đây là khóa ngoại THỨ HAI trỏ về cùng bảng `zones` (khác với field `zone`
    // phía trên) — cùng 1 Entity Zone nhưng đóng 2 vai trò khác nhau trong cùng
    // 1 luật: một bên là "Zone bị giám sát", một bên là "Zone được gợi ý sang".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_zone_id")
    private Zone suggestedZone;

    // Giờ bắt đầu khung thời gian luật này có hiệu lực (ví dụ 06:00).
    // Có thể NULL nếu luật áp dụng cho toàn bộ 24 giờ (không giới hạn khung giờ).
    @Column(name = "start_time")
    private java.time.LocalTime startTime;

    // Giờ kết thúc khung thời gian luật này có hiệu lực (ví dụ 22:00).
    // Lưu ý: nếu startTime > endTime (ví dụ 22:00 -> 06:00) nghĩa là khung giờ
    // "qua đêm" (overnight) — logic so khớp khung giờ này nằm ở ZoneRoutingService,
    // không xử lý ở Entity này.
    @Column(name = "end_time")
    private java.time.LocalTime endTime;

    // Cờ đánh dấu đây có phải luật MẶC ĐỊNH (fallback) hay không — dùng khi
    // không có luật nào khác khớp khung giờ/ngưỡng hiện tại thì rơi về luật này.
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    // Cờ Soft Delete: true = luật đang hoạt động, false = luật đã bị "xóa mềm"
    // (vẫn còn trong Database để giữ lịch sử, nhưng bị loại khỏi mọi tính toán
    // điều phối runtime).
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
