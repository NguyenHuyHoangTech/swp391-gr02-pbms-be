/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Floor entity.
 *               Provides standard CRUD operations for parking floors.
 * @Dependencies: 
 * - Floor (com.pbms.modules.infrastructure.domain.Floor)
 */  
package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FloorRepository extends JpaRepository<Floor, Long> {
}
