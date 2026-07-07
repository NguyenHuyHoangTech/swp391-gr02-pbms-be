package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.PricingBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PricingBlockRepository extends JpaRepository<PricingBlock, Long> {
}

