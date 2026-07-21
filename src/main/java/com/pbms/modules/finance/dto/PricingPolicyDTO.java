package com.pbms.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingPolicyDTO {
    private Long id;
    private String policyName;
    private Long vehicleTypeId;
    private Integer globalBaseMins;
    private BigDecimal globalBaseFee;

    private BigDecimal monthlyRate;
    private String status;
    @Builder.Default
    private List<PricingShiftDTO> shifts = new ArrayList<>();
}

