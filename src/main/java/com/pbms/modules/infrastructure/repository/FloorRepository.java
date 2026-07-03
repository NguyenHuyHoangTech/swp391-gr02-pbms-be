package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<Floor, Long> {
}

