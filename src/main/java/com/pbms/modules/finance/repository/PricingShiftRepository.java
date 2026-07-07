package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.PricingShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PricingShiftRepository extends JpaRepository<PricingShift, Long> {
}

