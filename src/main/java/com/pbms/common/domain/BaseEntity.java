package com.pbms.common.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity cha dùng chung cho các entity khác trong project.
 * Những entity kế thừa BaseEntity sẽ tự có id, createdAt và updatedAt.
 * Class này dùng TimeProvider trong common.utils để lấy thời gian hiện tại của hệ thống.
 *
 * Pseudo code:
 * 1. Tạo các field chung cho entity gồm id, createdAt và updatedAt.
 * 2. Khi một entity mới được lưu vào database, tự gán createdAt và updatedAt.
 * 3. Khi một entity được cập nhật, tự cập nhật lại updatedAt.
 * 4. Cho các entity khác kế thừa để tránh viết lặp lại các field này.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Tự động chạy trước khi entity được insert vào database.
     * Method này gán thời gian tạo và thời gian cập nhật ban đầu cho entity.
     *
     * Pseudo code:
     * 1. Lấy thời gian hiện tại từ TimeProvider.
     * 2. Gán thời gian đó vào createdAt.
     * 3. Gán thời gian đó vào updatedAt.
     * 4. Sau đó entity mới được lưu vào database.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = com.pbms.common.utils.TimeProvider.now();
        updatedAt = com.pbms.common.utils.TimeProvider.now();
    }

    /**
     * Tự động chạy trước khi entity được update trong database.
     * Method này cập nhật lại thời gian chỉnh sửa gần nhất của entity.
     *
     * Pseudo code:
     * 1. Lấy thời gian hiện tại từ TimeProvider.
     * 2. Gán thời gian đó vào updatedAt.
     * 3. Sau đó entity được cập nhật trong database.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = com.pbms.common.utils.TimeProvider.now();
    }
}