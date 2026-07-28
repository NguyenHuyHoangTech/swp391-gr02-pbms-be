/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-03
 * @Description: Lớp DTO chuyên sâu hơn để ghi nhận hoặc cập nhật vé sự cố (Incident Ticket).
 * Chứa các trường đặc thù xử lý từng loại sự cố cụ thể như thẻ mất, sai biển số, đỗ sai bãi.
 * @Dependencies: Không có
 */
package com.pbms.modules.incident.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IncidentTicketRequest {
    /** ID của phiên đỗ xe (ParkingSession) đang gặp sự cố */
    private Long sessionId;

    /** Biển số xe để tìm kiếm phiên đỗ xe nếu không cung cấp sessionId */
    private String plate;

    /**
     * Phân loại sự cố.
     * Quy tắc giá trị:
     * - "LPR_MISMATCH": Biển số nhận diện sai
     * - "LOST_CARD": Mất thẻ RFID
     * - "DAMAGED_CARD": Thẻ hỏng
     * - "ZONE_VIOLATION": Đỗ sai khu vực quy định
     */
    private String issueType;

    /** Mức độ ưu tiên xử lý (HIGH, MEDIUM, LOW) */
    private String priority;

    /** Mô tả chi tiết vấn đề từ nhân viên */
    private String description;

    /** (Dành cho sự cố LPR_MISMATCH) Biển số đúng cần cập nhật lại */
    private String correctPlateNumber;

    /** (Dành cho sự cố LOST_CARD) Số tiền bồi thường thẻ cần thu của khách */
    private BigDecimal fineAmount;

    /**
     * (Dành cho sự cố LOST_CARD) URL của giấy tờ tùy thân khách hàng đã tải lên để
     * xác minh
     */
    private String uploadedDocUrl;

    /** ID loại phương tiện được yêu cầu xác thực */
    @jakarta.validation.constraints.NotNull(message = "Vehicle type is required")
    private Long vehicleTypeId;

    /**
     * (Dành cho sự cố DAMAGED_CARD) Nguyên nhân hỏng thẻ ("NATURAL" hoặc "USER")
     */
    private String damageCause;
}
