/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Entity storing the peak occupancy a zone reached within one hourly
 *               window. Mapped to the 'zone_hourly_trends' table in the database.
 * @Dependencies:
 * - Zone (com.pbms.modules.infrastructure.domain.Zone)
 */
package com.pbms.modules.operation.domain;

import com.pbms.modules.infrastructure.domain.Zone;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "zone_hourly_trends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneHourlyTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "time_window", nullable = false)
    private LocalDateTime timeWindow;

    @Column(name = "occupancy_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal occupancyPct;

}
