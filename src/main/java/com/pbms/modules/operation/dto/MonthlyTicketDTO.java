/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-06
 * @Description: DTO for transferring Monthly Ticket data to the Frontend.
 */
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
    
    /** 
     * Derived status for frontend display.
     * ACTIVE, EXPIRED, EXPIRING_SOON
     */
    private String status;
    
    private String startDate;
    
    private String endDate;
    
    private boolean hasBeenUsed;
}
