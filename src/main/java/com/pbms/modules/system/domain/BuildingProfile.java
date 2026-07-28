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
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "building_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildingProfile extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 50)
    private String hotline;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "is_247", columnDefinition = "BIT DEFAULT 0")
    private Boolean is247;

    @Column(name = "operating_start", length = 5)
    private String operatingStart;

    @Column(name = "operating_end", length = 5)
    private String operatingEnd;

    @Column(columnDefinition = "VARCHAR(MAX)")
    private String rules;
}

