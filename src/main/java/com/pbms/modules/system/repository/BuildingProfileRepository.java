package com.pbms.modules.system.repository;

import com.pbms.modules.system.domain.BuildingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingProfileRepository extends JpaRepository<BuildingProfile, Long> {
}

