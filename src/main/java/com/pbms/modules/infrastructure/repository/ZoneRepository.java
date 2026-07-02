/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Zone entity.
 *               Provides standard CRUD operations for parking zones.
 * @Dependencies: 
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */  
package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
}
