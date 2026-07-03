package com.pbms.modules.identity.repository;

import com.pbms.modules.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: Repository for accessing user account records.
 *               Provides JPA and specification-based queries for the users table.
 * @Dependencies:
 * - User (com.pbms.modules.identity.domain.User)
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
}

