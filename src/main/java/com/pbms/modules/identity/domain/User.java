package com.pbms.modules.identity.domain;

import com.pbms.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: Entity representing an application user account in the parking system.
 *               Mapped to the 'users' table in the database.
 * @Dependencies:
 * - BaseEntity (com.pbms.common.domain.BaseEntity)
 */
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

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(length = 50)
    @Builder.Default
    private String status = "ACTIVE";
}

