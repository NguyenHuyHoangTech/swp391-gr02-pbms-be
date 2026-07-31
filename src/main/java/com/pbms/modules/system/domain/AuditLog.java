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
 * @created 10/05/2026
 */
package com.pbms.modules.system.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 500)
    private String resource;

    @Column(name = "old_value", columnDefinition = "NVARCHAR(MAX)")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "NVARCHAR(MAX)")
    private String newValue;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;
}

