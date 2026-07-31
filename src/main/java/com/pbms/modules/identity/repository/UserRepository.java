// TODO(Member1): Full implementation pending - CORE ARCHITECT & CLOUD DEVOPS
package com.pbms.modules.identity.repository;

import com.pbms.modules.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
