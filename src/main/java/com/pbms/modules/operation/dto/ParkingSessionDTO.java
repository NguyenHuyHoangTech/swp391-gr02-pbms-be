package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class ParkingSessionDTO {
    private String plateNumber;
    private Long gateId;
    private String rfidCardCode;
}

