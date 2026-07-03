package com.pbms.modules.customer.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingDTO {
    private String vehicleType;
    private String slotId;
    private LocalDateTime arrivalTime;
}

