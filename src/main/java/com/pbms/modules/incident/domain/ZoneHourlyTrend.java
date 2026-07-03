package com.pbms.modules.incident.domain;

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

    @Column(name = "revenue_generated", nullable = false, precision = 18, scale = 2)
    private BigDecimal revenueGenerated;

    @Column(name = "entries_count", nullable = false)
    private Integer entriesCount;

    @Column(name = "exits_count", nullable = false)
    private Integer exitsCount;
}

