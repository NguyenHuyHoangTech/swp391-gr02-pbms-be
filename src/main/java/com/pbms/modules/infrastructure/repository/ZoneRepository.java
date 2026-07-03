package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
}

