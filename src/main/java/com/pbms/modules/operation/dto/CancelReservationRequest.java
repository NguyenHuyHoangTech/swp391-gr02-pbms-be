/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: DTO containing banking information required to process a reservation cancellation and refund.
 * @Dependencies: None
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CancelReservationRequest {
    private String bankName;
    private String accountNumber;
    private String accountName;
}

