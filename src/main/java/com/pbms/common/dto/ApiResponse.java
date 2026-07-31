// TO BE IMPLEMENTED BY MEMBER 1 (CORE ARCHITECT & CLOUD DEVOPS)
package com.pbms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private String status;
    private int code;
    private String message;
    private T data;

    // Ghi lại thời gian API này phản hồi.
    @Builder.Default  //khi dùng @Builder: Báo cho Lombok biết hãy lấy giá trị mặc định này nếu lúc tạo object không truyền timestamp vào.
    private LocalDateTime timestamp = com.pbms.common.utils.TimeProvider.now();

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .code(code)
                .message(message)
                .build();
    }
}


