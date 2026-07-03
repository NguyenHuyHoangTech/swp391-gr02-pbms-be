package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CancelReservationRequest {
    private String bankName;
    private String accountNumber;
    private String accountName;
}

