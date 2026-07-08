package com.pbms.modules.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RefundRequestDTO {
    private String id;
    private String customerName;
    private String customerEmail;
    private String registeredName;
    private String plateNumber;
    private String bookingTime;
    private String expectedInTime;
    private String cancelTime;
    private BigDecimal paidAmount;
    private BigDecimal penaltyFee;
    private BigDecimal refundAmount;
    private String status;
    private String bankName;
    private String accountNumber;
    private String accountName;
    private String rejectReason;
    private String referenceType;
    private String proofUrl;
}

