/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO sử dụng để phát các sự kiện quét (scan) thông qua WebSocket.
 * Dùng để cập nhật trực tiếp lên màn hình Console của nhân viên khi có xe quẹt thẻ hoặc camera nhận diện biển số.
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
public class ScanEventDTO {
    /** ID của cổng đang xảy ra sự kiện quét */
    private Long gateId;

    /**
     * Loại hành động.
     * Quy tắc giá trị:
     * - "IN": Check-in (Xe vào)
     * - "OUT": Check-out (Xe ra)
     */
    private String actionType;

    /** Biển số xe nhận diện được từ camera IoT */
    private String plateNumber;

    /** Tên loại phương tiện */
    private String vehicleType;

    /** Mã thẻ RFID được quét */
    private String rfid;
    
    /** Ảnh toàn cảnh (panorama) thu được từ camera IoT (được mã hóa base64) */
    private String imageBase64;

    /** Ảnh chụp cắt biển số thu được từ camera IoT (được mã hóa base64) */
    private String lprImageBase64;

    /** Biển số xe được lưu ở lúc Check-in (Dùng đối chiếu khi Check-out) */
    private String plateNumberIn;
    
    /** Ảnh toàn cảnh (panorama) lưu ở lúc Check-in (Dùng đối chiếu khi Check-out) */
    private String picInPanorama;

    /** Ảnh chụp cắt biển số lưu ở lúc Check-in (Dùng đối chiếu khi Check-out) */
    private String picInFace;

    /** Thời điểm xe bắt đầu Check-in */
    private LocalDateTime timeIn;
    
    /** ID khu vực đỗ xe được hệ thống gợi ý (Dành cho Check-in) */
    private Long suggestedZoneId;

    /** Tên khu vực đỗ xe được hệ thống gợi ý (Dành cho Check-in) */
    private String suggestedZoneName;
    
    /** Thời gian đỗ xe tính bằng phút (Tính đến hiện tại) */
    private Long durationMinutes;

    /** Phí đỗ xe dự kiến tính đến hiện tại */
    private java.math.BigDecimal expectedFee;
    
    /**
     * Phân loại khách hàng.
     * Quy tắc giá trị:
     * - "GUEST": Khách vãng lai
     * - "PREBOOKED": Khách đã đặt chỗ trước
     * - "MONTHLY": Khách sử dụng vé tháng
     */
    private String customerType;
}
