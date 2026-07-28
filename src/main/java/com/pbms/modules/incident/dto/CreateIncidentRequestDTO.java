/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-03
 * @Description: Lớp DTO dùng để nhận yêu cầu tạo sự cố mới từ giao diện người dùng (Thường là form tạo nhanh sự cố).
 * @Dependencies: Không có
 */
package com.pbms.modules.incident.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateIncidentRequestDTO {
    /** Loại sự cố (Ví dụ: LOST_CARD, LPR_MISMATCH, ZONE_VIOLATION) */
    private String type;

    /** Biển số xe liên quan đến sự cố */
    private String plate;

    /** Mã thẻ RFID liên quan (nếu có) */
    private String rfid;

    /** Mô tả chi tiết về tình trạng sự cố */
    private String description;

    /** Mức phí phạt cơ bản dự kiến thu ban đầu (nếu có) */
    private BigDecimal baseFee;
}
