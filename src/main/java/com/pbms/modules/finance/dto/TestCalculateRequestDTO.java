package com.pbms.modules.finance.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TestCalculateRequestDTO {
    private PricingPolicyDTO policy;
    private LocalDateTime timeIn;
    private LocalDateTime timeOut;
}
