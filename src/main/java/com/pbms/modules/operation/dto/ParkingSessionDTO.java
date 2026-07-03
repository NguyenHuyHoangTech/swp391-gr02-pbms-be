/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO đơn giản để truyền tải thông tin cơ bản của một phiên đỗ xe.
 * Có thể được sử dụng làm object chuyển đổi trong các API nội bộ hoặc tìm kiếm nhanh.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class ParkingSessionDTO {
    /** Biển số xe đang đỗ */
    private String plateNumber;

    /** ID của cổng mà xe đã sử dụng để vào bãi */
    private Long gateId;

    /** Mã thẻ RFID gắn với phiên đỗ xe này */
    private String rfidCardCode;
}
v