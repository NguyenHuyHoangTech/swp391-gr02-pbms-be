package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CameraScanDTO {
    private String gateId;
    private String plateNumber;
    private Double confidence;
    private String imageBase64;
}

