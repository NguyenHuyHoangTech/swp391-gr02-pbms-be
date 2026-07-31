/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: DTO chứa dữ liệu từ client gửi lên để tạo một đơn đặt chỗ (prebooking) mới.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateReservationRequest {
    
    // Bước 1: Khai báo ID loại phương tiện (bắt buộc)
    @NotNull(message = "Vehicle type is required")
    private Long vehicleTypeId;
    
    // Bước 2: Khai báo biển số xe (bắt buộc)
    @NotBlank(message = "Plate number is required")
    private String plateNumber;
    
    // Bước 3: Khai báo ID khu vực bãi đỗ mong muốn (bắt buộc)
    @NotNull(message = "Zone ID is required")
    private Long zoneId;
    
    // Bước 4: Khai báo thời gian dự kiến xe sẽ vào bãi (Format ISO)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedEntryTime;
    
    // Bước 5: Khai báo thời lượng dự kiến gửi xe (tính bằng phút)
    private Integer expectedDurationMinutes;

    /**
     * Hàm setter tùy chỉnh để tự động chuẩn hóa biển số xe khi parse JSON.
     * Logic mã giả:
     * 1. Khi client gửi JSON, thư viện Jackson sẽ gọi hàm setter này.
     * 2. Gọi tiện ích LicensePlateUtils.normalize() để viết hoa toàn bộ ký tự và xóa khoảng trắng dư thừa.
     * 3. Gán giá trị đã chuẩn hóa vào thuộc tính plateNumber.
     */
    public void setPlateNumber(String plateNumber) {
        this.plateNumber = com.pbms.modules.infrastructure.utils.LicensePlateUtils.normalize(plateNumber);
    }
}
