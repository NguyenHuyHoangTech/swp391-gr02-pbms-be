package com.pbms.modules.finance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pricing_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private PricingShift shift;

    @Column(name = "block_order", nullable = false)
    private Integer blockOrder;

    @Column(name = "duration_mins", nullable = false)
    private Integer durationMins;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal fee;
}

