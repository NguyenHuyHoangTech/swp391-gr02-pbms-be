/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: JPA Repository for the Gate entity.
 *               Provides methods to find gates by floor.
 * @Dependencies: 
 * - Gate (com.pbms.modules.infrastructure.domain.Gate)
 */  
package com.pbms.modules.infrastructure.repository;

import com.pbms.modules.infrastructure.domain.Gate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GateRepository extends JpaRepository<Gate, Long> {
    List<Gate> findByFloorId(Long floorId);
    List<Gate> findByGateType(String gateType);
}
