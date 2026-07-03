package com.pbms.modules.operation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTicketDTO {
    private String id;
    private String user;
    private String email;
    private String phone;
    private String plate;
    private String type;
    private Long vehicleTypeId;
    private String status; // ACTIVE, EXPIRING_SOON, EXPIRED, CANCELED, PENDING
    private String startDate;
    private String endDate;
    private Boolean hasBeenUsed;
}

