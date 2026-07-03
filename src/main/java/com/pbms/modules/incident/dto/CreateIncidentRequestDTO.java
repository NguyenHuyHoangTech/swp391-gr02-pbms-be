package com.pbms.modules.incident.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateIncidentRequestDTO {
    private String type;
    private String plate;
    private String rfid;
    private String description;
    private BigDecimal baseFee;
}

