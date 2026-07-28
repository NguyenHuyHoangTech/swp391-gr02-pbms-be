/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: DTO chứa thông tin ngân hàng do khách hàng cung cấp để yêu cầu hoàn tiền khi hủy đặt chỗ.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CancelReservationRequest {
    // Tên ngân hàng thụ hưởng (VD: Vietcombank, TPBank)
    private String bankName;
    
    // Số tài khoản ngân hàng
    private String accountNumber;
    
    // Tên chủ tài khoản
    private String accountName;
}
