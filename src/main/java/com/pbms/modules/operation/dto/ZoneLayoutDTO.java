/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-15
 * @Description: Data Transfer Object for Zone Layout information.
 * @Dependencies: lombok.Data
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

// Thông tin vị trí/góc xoay của 1 Zone trên bản đồ Space Map - dùng khi FE
// cần gửi/nhận riêng phần layout mà không kèm toàn bộ dữ liệu Zone.
@Data
public class ZoneLayoutDTO {

    // Toạ độ X (pixel) của góc xoay Zone trên bản đồ.
    private Double layoutX;

    // Toạ độ Y (pixel) của góc xoay Zone trên bản đồ.
    private Double layoutY;

    // Góc xoay hiện tại của Zone (0/90/180/270 độ).
    private Double rotation;
}
