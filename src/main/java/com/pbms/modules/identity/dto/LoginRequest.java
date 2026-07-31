/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC ĐÓNG GÓI DỮ LIỆU (DATA TRANSFER OBJECT)
 * =========================================================================================
 * 
 * BƯỚC 1: BẢO VỆ DỮ LIỆU LÕI (ENCAPSULATION)
 * - Minh chứng: Sử dụng DTO thay vì Entity để giao tiếp với Client. Điều này giúp 
 *   giấu đi cấu trúc thật của CSDL, chỉ phơi bày những trường dữ liệu an toàn.
 * 
 * BƯỚC 2: TỰ ĐỘNG HÓA BOILERPLATE CODE VỚI LOMBOK
 * - Minh chứng: Ký hiệu @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor 
 *   giúp tự động sinh ra Getter, Setter, Builder mà không cần viết code thủ công.
 * 
 * BƯỚC 3: KIỂM TRA TÍNH HỢP LỆ (VALIDATION)
 * - Minh chứng: Có thể sử dụng các ký hiệu như @NotBlank, @NotNull, @Email 
 *   để bắt lỗi dữ liệu ngay từ vòng gửi xe (Controller) trước khi xuống Service.
 * 
 * @author Phạm Anh Tuấn
 * @created 03/05/2026
 */
package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}

