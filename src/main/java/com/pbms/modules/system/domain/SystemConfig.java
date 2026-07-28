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
@Table(name = "system_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "VARCHAR(MAX)")
    private String configValue;

    @Column(length = 500)
    private String description;
}

