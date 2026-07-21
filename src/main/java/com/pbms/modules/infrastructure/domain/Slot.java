/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: Entity representing a specific parking Slot within a Zone. 
 *               Mapped to the 'slots' table in the database.
 * @Dependencies: 
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */  
package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "slots", indexes = {
    @Index(name = "idx_zone_status", columnList = "zone_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "slot_name", nullable = false, length = 50)
    private String slotName;

    /** 
     * Occupancy status of the slot.
     * Values: AVAILABLE, OCCUPIED, DISABLED 
     */
    @Column(length = 50)
    @Builder.Default
    private String status = "AVAILABLE";

    /** License plate of the currently parked vehicle (if any) */
    @Column(name = "current_plate", length = 50)
    private String currentPlate;

    @Version
    @Builder.Default
    private Integer version = 1;
}
