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
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private String action;
    private String resource;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String description;
    private LocalDateTime createdAt;
    private ActorDTO actor;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorDTO {
        private String email;
    }
}
