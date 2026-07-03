/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO (Data Transfer Object) dùng để chứa dữ liệu yêu cầu khi một phương tiện thực hiện Check-out tại cổng.
 * Dữ liệu được thu thập từ camera LPR, đầu đọc RFID để đối chiếu với dữ liệu Check-in nhằm tính toán phí và xử lý xuất bến.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CheckOutRequestDTO {
    /** ID của cổng barrier nơi phương tiện thực hiện check-out */
    private Long gateId;

    /** Biển số xe nhận diện được lúc đi ra */
    private String plateNumber;

    /** Tên loại phương tiện lúc đi ra */
    private String vehicleType;

    /** Mã thẻ RFID được quẹt lúc đi ra để đối chiếu với thông tin check-in */
    private String rfid;

    /** Ảnh toàn cảnh (panorama) chụp tại thời điểm check-out (được mã hóa base64) */
    private String imageBase64;

    /** Ảnh chụp cắt biển số (LPR crop) tại thời điểm check-out (được mã hóa base64) */
    private String lprImageBase64;

    /** Phương thức thanh toán được chọn (Ví dụ: CASH, VNPay, Momo) */
    private String paymentMethod;

    /** Tổng số tiền phí cuối cùng khách hàng cần thanh toán. Đơn vị: VNĐ */
    private java.math.BigDecimal totalFee;

    /**
     * Phân loại khách hàng khi ra cổng.
     * Quy tắc giá trị:
     * - "GUEST": Khách vãng lai
     * - "PREBOOKED": Khách đã đặt chỗ trước
     * - "MONTHLY": Khách sử dụng vé tháng
     */
    private String customerType;
}
