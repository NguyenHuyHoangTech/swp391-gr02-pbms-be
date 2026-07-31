/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: DTO chứa dữ liệu chi tiết của một đơn đặt chỗ để trả về cho Frontend.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReservationDTO {
    // ID của đơn đặt chỗ
    private Long id;
    
    // Biển số xe đặt chỗ
    private String plateNumber;
    
    // Tên loại phương tiện
    private String vehicleType;
    
    // ID của loại phương tiện
    private Long vehicleTypeId;
    
    // Mã thẻ RFID liên kết
    private String rfid;
    
    // Tên khu vực bãi đỗ đã đặt
    private String zoneName;
    
    // Tên ô đỗ (nếu được cấp phát tĩnh)
    private String slotName;
    
    // Thời gian dự kiến vào bãi
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedEntryTime;
    
    // Thời lượng dự kiến gửi (phút)
    private Integer expectedDurationMinutes;
    
    // Trạng thái đơn: PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW
    private String status; 
    
    // Phí đặt chỗ đã thu
    private BigDecimal reservationFee;
    
    // Giờ vào thực tế (chuỗi)
    private String actualIn;
    
    // Giờ ra thực tế (chuỗi)
    private String actualOut;
    
    // Phí phạt nếu khách hàng đến quá trễ hoặc không đến (No-show)
    private BigDecimal penaltyFee;
    
    // Email người dùng đặt chỗ
    private String userEmail;
    
    // Thời điểm tạo đơn
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    // Các trường liên quan đến việc hoàn tiền (nếu hủy đơn)
    private String refundStatus; // Trạng thái hoàn tiền
    private BigDecimal refundAmount; // Số tiền được hoàn
    private Long refundRequestId; // ID của yêu cầu hoàn tiền
    private String rejectReason; // Lý do từ chối hoàn tiền (nếu có)
    private String refundProofUrl; // Link hình ảnh minh chứng hoàn tiền
}
