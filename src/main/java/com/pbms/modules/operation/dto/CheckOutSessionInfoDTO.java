/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO chứa thông tin phiên đỗ xe hiện tại để hiển thị cho nhân viên xác nhận trước khi Check-out.
 * Chứa các thông tin đối chiếu giữa lúc vào và lúc ra, bao gồm cả tính toán phí dự kiến.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckOutSessionInfoDTO {
    /** Biển số xe được ghi nhận lúc Check-in */
    private String plateNumberIn;

    /** Mã thẻ RFID đang được sử dụng cho phiên đỗ xe này */
    private String rfid;

    /** Loại phương tiện (Ví dụ: Xe máy, Ô tô) */
    private String vehicleType;

    /**
     * Phân loại khách hàng (Ví dụ: "GUEST", "PREBOOKED", "MONTHLY")
     */
    private String customerType;
    
    /** Ảnh toàn cảnh (panorama) chụp tại thời điểm Check-in (được mã hóa base64) */
    private String picInPanorama;

    /** Ảnh chụp cắt biển số (LPR) tại thời điểm Check-in (được mã hóa base64) */
    private String picInFace;
    
    /** Thời gian phương tiện bắt đầu vào bãi (Check-in) */
    private LocalDateTime timeIn;

    /** Thời gian hiện tại (thời điểm yêu cầu xuất thông tin Check-out) */
    private LocalDateTime timeOut;

    /** Khu vực đỗ xe được gợi ý lúc Check-in */
    private String suggestedZoneName;
    
    /** Tổng thời gian đỗ xe tính bằng phút */
    private Long durationMinutes;

    /** Phí đỗ xe dự kiến dựa trên bảng giá và thời gian đỗ */
    private java.math.BigDecimal expectedFee;

    /** Tiền phạt bổ sung từ các sự cố (Incident) nếu có */
    private java.math.BigDecimal feePenalty;

    /** Số tiền được giảm giá (áp dụng nếu có khuyến mãi hoặc xử lý khiếu nại) */
    private java.math.BigDecimal discountFee;
    
    /** Thời gian dự kiến vào bãi (Dành cho khách đặt trước) */
    private LocalDateTime bookedTimeIn;

    /** Thời gian dự kiến ra bãi (Dành cho khách đặt trước) */
    private LocalDateTime bookedTimeOut;

    /** Số phút đỗ quá giờ so với thời gian đặt trước (Dành cho khách đặt trước) */
    private Long overtimeMinutes;

    /** Trạng thái hiện tại của phiên đỗ xe (Ví dụ: ACTIVE, LOCKED, COMPLETED) */
    private String status;
}
