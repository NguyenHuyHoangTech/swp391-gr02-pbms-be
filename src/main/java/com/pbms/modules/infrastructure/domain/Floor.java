/**  
 * @Author: Nguyen Huu Thanh (TH)
 * @Date: 2026-07-02  
 * @Description: Entity representing a Floor in the parking building. 
 *               Mapped to the 'floors' table in the database.
 * @Dependencies: 
 * - BuildingProfile (com.pbms.modules.system.domain.BuildingProfile)
 */  
package com.pbms.modules.infrastructure.domain;

import com.pbms.modules.system.domain.BuildingProfile;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "floors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingProfile building;

    @Column(name = "floor_name", nullable = false, length = 100)
    private String floorName;

    @Column(name = "floor_level", nullable = false)
    private Integer floorLevel;

    @Column(nullable = false)
    private Integer capacity;

    /** Allowed vehicle type on this floor (e.g., CAR, MOTORBIKE) */
    @Column(name = "floor_type", length = 50)
    private String floorType;

    @Column(name = "map_cols")
    private Integer mapCols;

    @Column(name = "map_rows")
    private Integer mapRows;
}
