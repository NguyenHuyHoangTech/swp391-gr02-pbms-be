/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the RoutingRule entity.
 *               Provides methods to find active routing rules by zone.
 * @Dependencies: 
 * - RoutingRule (com.pbms.modules.infrastructure.domain.RoutingRule)
 */  
package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
    // TODO(TH): unused - RoutingRuleService and ZoneRoutingService both filter
    // active rules manually via findAll() + stream() instead of calling this.
    // Confirm intended caller before removing.
    List<RoutingRule> findAllByZoneIdAndIsActiveTrue(Long zoneId);
}
