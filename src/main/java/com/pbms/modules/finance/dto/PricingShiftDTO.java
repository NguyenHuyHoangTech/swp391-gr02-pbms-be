package com.pbms.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingShiftDTO {
    private Long id;
    private String shiftName;
    private String startTime; // "HH:mm"
    private String endTime; // "HH:mm"
    private Integer totalDurationMins;
    @Builder.Default
    private List<PricingBlockDTO> blocks = new ArrayList<>();
}

