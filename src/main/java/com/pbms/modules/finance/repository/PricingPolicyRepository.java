package com.pbms.modules.finance.repository;

import com.pbms.modules.finance.domain.PricingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, Long> {
    Optional<PricingPolicy> findByVehicleTypeIdAndStatus(Long vehicleTypeId, String status);
}

