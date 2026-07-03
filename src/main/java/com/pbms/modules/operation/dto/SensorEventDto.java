package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class SensorEventDto {
    private String sensorId; // We map this to Slot ID in this context for simplicity
    private String status; // OCCUPIED, AVAILABLE
}

