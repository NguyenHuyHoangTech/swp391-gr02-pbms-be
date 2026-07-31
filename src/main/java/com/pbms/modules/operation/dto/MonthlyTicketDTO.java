/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-06
 * @Description: DTO chứa dữ liệu của vé tháng để trả về cho Frontend hiển thị.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTicketDTO {
    
    // ID của vé tháng (dạng chuỗi hoặc số tùy frontend)
    private String id;
    
    // Tên người dùng sở hữu vé
    private String user;
    
    // Email liên hệ của người dùng
    private String email;
    
    // Số điện thoại của người dùng
    private String phone;
    
    // Biển số xe được đăng ký
    private String plate;
    
    // Loại xe (VD: Xe máy, Ô tô)
    private String type;
    
    // ID của loại xe
    private Long vehicleTypeId;
    
    // Trạng thái vé hiển thị cho FE: ACTIVE (Hoạt động), EXPIRED (Hết hạn), EXPIRING_SOON (Sắp hết hạn)
    private String status;
    
    // Ngày bắt đầu hiệu lực (Format chuỗi)
    private String startDate;
    
    // Ngày kết thúc hiệu lực (Format chuỗi)
    private String endDate;
    
    // Cờ đánh dấu vé này đã từng được sử dụng để vào bãi hay chưa
    private Boolean hasBeenUsed;
    
    // Cờ báo hiệu xe đang nằm trong bãi hay không
    private Boolean inParkingLot;
    
    // Mã thẻ RFID nếu có liên kết
    private String rfid;
}
