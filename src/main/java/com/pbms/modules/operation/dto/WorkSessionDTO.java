package com.pbms.modules.operation.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WorkSessionDTO {
    private Long gateId;
    private BigDecimal declaredCash;
    private String varianceReason;
}

