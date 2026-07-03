package com.pbms.modules.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlotDTO {
    private String id; // Use String to match frontend (e.g. "A01", or just "1" but frontend expects string)
    private String name;
    private String status; // EMPTY, OCCUPIED, DISABLED
}

