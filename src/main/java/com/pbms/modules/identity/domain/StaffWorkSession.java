/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC ÁNH XẠ DỮ LIỆU (ORM / JPA ENTITY)
 * =========================================================================================
 * 
 * BƯỚC 1: ĐỊNH NGHĨA THỰC THỂ CSDL
 * - Minh chứng 1: Ký hiệu @Entity đánh dấu class này là một bảng trong CSDL.
 * - Minh chứng 2: @Table(name = '...') quy định chính xác tên bảng trong SQL.
 * 
 * BƯỚC 2: ÁNH XẠ CÁC TRƯỜNG DỮ LIỆU (FIELDS MAPPING)
 * - Minh chứng 1: @Id và @GeneratedValue xác định khóa chính tự tăng (Primary Key).
 * - Minh chứng 2: @Column ánh xạ tên biến Java với tên cột tương ứng trong CSDL.
 * 
 * BƯỚC 3: QUẢN LÝ QUAN HỆ (RELATIONSHIPS)
 * - Minh chứng: Các ký hiệu @OneToMany, @ManyToOne, @JoinColumn giúp JPA 
 *   tự động xử lý các truy vấn JOIN giữa nhiều bảng.
 * 
 * @author Phạm Anh Tuấn
 * @created 03/05/2026
 */
package com.pbms.modules.identity.domain;

import com.pbms.modules.infrastructure.domain.Gate;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff_work_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffWorkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", nullable = false)
    private Gate gate;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(nullable = false, length = 50)
    private String status; // ACTIVE, COMPLETED

    @Column(name = "expected_revenue")
    private java.math.BigDecimal expectedRevenue;

    @Column(name = "expected_cash_revenue")
    private java.math.BigDecimal expectedCashRevenue;

    @Column(name = "expected_other_revenue")
    private java.math.BigDecimal expectedOtherRevenue;



    @Column(name = "work_gate_type", length = 50)
    private String workGateType; // ENTRY, EXIT, PATROL
}

