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
public class RevenueRecordDTO {
    private String date;
    private String vehicleType;
    private String gateName;
    private String revenueSource;
    private String paymentMethod;
    private BigDecimal totalRevenue;
    private Long totalTransactions;
}

