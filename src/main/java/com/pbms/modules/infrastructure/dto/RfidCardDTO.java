package com.pbms.modules.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RfidCardDTO {
    private String uid;
    private String visualId;
    private String status;
    private String location;
}

