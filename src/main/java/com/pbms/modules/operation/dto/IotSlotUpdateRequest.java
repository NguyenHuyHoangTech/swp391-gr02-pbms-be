package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class IotSlotUpdateRequest {
    private Long slotId;
    private String status; // OCCUPIED, EMPTY
}

