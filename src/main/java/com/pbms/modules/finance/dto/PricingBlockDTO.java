package com.pbms.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingBlockDTO {
    private Long id;
    private Integer blockOrder;
    private Integer durationMins;
    private BigDecimal fee;
}

