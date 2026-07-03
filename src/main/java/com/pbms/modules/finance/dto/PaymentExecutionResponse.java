package com.pbms.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentExecutionResponse {
    // [PBMS-76] DTO chuẩn hóa dữ liệu trả về cho Front-end khi tạo link thanh toán

    private String paymentUrl;     // Đường link để user bấm vào thanh toán
    private String orderCode;      // Mã đơn hàng (vnp_TxnRef, mã PayOS,...)
    private BigDecimal amount;     // Số tiền
    private String gateway;        // PAYOS, VNPAY, PAYPAL
    private String status;         // PENDING
    private Long referenceId;      // ID của Reservation hoặc MonthlyTicket
    private String referenceType;  // Phân loại: RESERVATION, MONTHLY_TICKET
}