/**  
 * @Author: Nguyen Huu Thanh (TH)  
 * @Date: 2026-07-02  
 * @Description: Entity defining routing rules for vehicles within the parking building. 
 *               Mapped to the 'routing_rules' table in the database.
 * @Dependencies: 
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */  
package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "routing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    /** Fill percentage threshold of the zone that triggers this rule */
    @Column(name = "fill_threshold_pct", nullable = false)
    private Integer fillThresholdPct;

    /** Suggested alternative zone when the rule is triggered */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_zone_id")
    private Zone suggestedZone;

    /** Start time for the rule validity (e.g., 08:00:00) */
    @Column(name = "start_time")
    private java.time.LocalTime startTime;

    /** End time for the rule validity (e.g., 17:00:00) */
    @Column(name = "end_time")
    private java.time.LocalTime endTime;

    /** Indicates whether this is a default fallback rule */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /** Activation status of the rule */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
