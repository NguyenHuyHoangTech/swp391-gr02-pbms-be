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
    List<RoutingRule> findAllByZoneIdAndIsActiveTrue(Long zoneId);
}
