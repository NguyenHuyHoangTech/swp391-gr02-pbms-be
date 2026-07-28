package com.pbms.modules.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CalculationResultDTO {
    private BigDecimal fee;
    private List<String> breakdown;
}
