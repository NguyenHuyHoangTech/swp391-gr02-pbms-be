package com.pbms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO dùng để chuẩn hóa format response trả về cho frontend.
 * Các controller trong project có thể dùng ApiResponse để trả về cùng một cấu trúc gồm status, code, message, data và timestamp.
 * GlobalExceptionHandler cũng có thể dùng class này để trả lỗi theo format thống nhất.
 * Thời gian response được lấy từ TimeProvider trong common.utils.
 *
 * Pseudo code:
 * 1. Tạo một object response chung cho toàn bộ API.
 * 2. Nếu request thành công, trả về status SUCCESS, code 200, message và data.
 * 3. Nếu request lỗi, trả về status ERROR, code lỗi và message.
 * 4. Tự gắn timestamp để biết thời điểm backend trả response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private String status;
    private int code;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = com.pbms.common.utils.TimeProvider.now();

    /**
     * Tạo response thành công cho API.
     * Method này dùng khi backend xử lý request thành công và cần trả data về frontend.
     *
     * Pseudo code:
     * 1. Nhận data cần trả về.
     * 2. Nhận message mô tả kết quả.
     * 3. Gán status là SUCCESS.
     * 4. Gán code là 200.
     * 5. Gán data và message vào response.
     * 6. Build và trả về ApiResponse.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Tạo response lỗi cho API.
     * Method này dùng khi backend xử lý request thất bại và cần trả lỗi về frontend.
     *
     * Pseudo code:
     * 1. Nhận mã lỗi cần trả về.
     * 2. Nhận message mô tả lỗi.
     * 3. Gán status là ERROR.
     * 4. Gán code theo mã lỗi được truyền vào.
     * 5. Không gán data vì request bị lỗi.
     * 6. Build và trả về ApiResponse.
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .code(code)
                .message(message)
                .build();
    }
}