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

import com.pbms.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_id", length = 100)
    private String googleId;


    @Column(name = "full_name", columnDefinition = "NVARCHAR(255)")
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String role; // ENUM: SUPER_ADMIN, MANAGER, STAFF, CUSTOMER

    @Column(length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, LOCKED
}

