// Author: Võ Trung Hiếu
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
public class RevenueTransactionDTO {
    private String checkoutTime;
    private String plate;
    private String vehicleType;
    private String gateName;
    private BigDecimal baseFee;
    private BigDecimal overtimeFee;
    private BigDecimal penaltyFee;
    private BigDecimal reservationFee;
    private BigDecimal totalFee;
    private String paymentMethod;
}
