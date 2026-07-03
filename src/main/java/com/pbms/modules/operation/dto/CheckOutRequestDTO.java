package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CheckOutRequestDTO {
    private Long gateId;
    private String plateNumber;
    private String vehicleType;
    private String rfid;
    private String imageBase64;
    private String lprImageBase64;
    private String paymentMethod;
    private java.math.BigDecimal totalFee;
    private String customerType; // "GUEST", "PREBOOKED", "MONTHLY"
}

