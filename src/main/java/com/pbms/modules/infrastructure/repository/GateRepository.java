package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Gate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GateRepository extends JpaRepository<Gate, Long> {
    List<Gate> findByFloorId(Long floorId);
}

