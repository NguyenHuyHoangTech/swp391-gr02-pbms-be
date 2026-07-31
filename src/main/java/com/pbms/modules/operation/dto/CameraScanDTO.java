/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO dùng để nhận luồng dữ liệu (stream/webhook) bắn từ các Camera LPR ngoại vi.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CameraScanDTO {
    /** ID hoặc mã thiết bị cổng đang gắn camera */
    private String gateId;

    /** Biển số xe được OCR (nhận diện) ra bằng AI */
    private String plateNumber;

    /** Tỉ lệ chính xác của kết quả nhận diện (Độ tin cậy từ 0.0 đến 1.0) */
    private Double confidence;

    /** Hình ảnh cắt ngang biển số hoặc xe được mã hóa dạng Base64 */
    private String imageBase64;
}
