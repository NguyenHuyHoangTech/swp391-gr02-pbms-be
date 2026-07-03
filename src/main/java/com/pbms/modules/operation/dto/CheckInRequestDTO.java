package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CheckInRequestDTO {
    private Long gateId;
    private String plateNumber;
    private String vehicleType;
    private Long vehicleTypeId;
    private String rfid;
    private String imageBase64;
    private String lprImageBase64;
    private String suggestedZoneName;
    private String customerType; // "GUEST", "PREBOOKED", "MONTHLY"
}

