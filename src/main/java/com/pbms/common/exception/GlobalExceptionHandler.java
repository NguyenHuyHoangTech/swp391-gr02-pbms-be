package com.pbms.common.exception;

import com.pbms.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Class xử lý lỗi chung cho toàn bộ backend.
 * Khi controller hoặc service ném lỗi, GlobalExceptionHandler sẽ bắt lỗi đó
 * và trả response về frontend theo format chung của ApiResponse trong common.dto.
 *
 * Pseudo code:
 * 1. Bắt các lỗi xảy ra trong controller.
 * 2. Phân loại lỗi theo từng loại exception.
 * 3. Ghi log nếu lỗi cần theo dõi.
 * 4. Tạo response lỗi bằng ApiResponse.error().
 * 5. Trả về HTTP status phù hợp cho frontend.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Xử lý lỗi validation khi dữ liệu request không hợp lệ.
     * Lỗi này thường xảy ra khi DTO dùng các annotation như @NotNull, @NotBlank, @Size hoặc @Email.
     *
     * Pseudo code:
     * 1. Lấy danh sách lỗi validation từ BindingResult.
     * 2. Lấy message của từng field bị lỗi.
     * 3. Gộp các message lỗi thành một chuỗi.
     * 4. Tạo ApiResponse lỗi với status code 400.
     * 5. Trả lỗi BAD_REQUEST về frontend.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMessage));
    }

    /**
     * Xử lý lỗi IllegalArgumentException.
     * Lỗi này thường được dùng khi tham số truyền vào không hợp lệ hoặc logic nghiệp vụ bị sai dữ liệu.
     *
     * Pseudo code:
     * 1. Bắt IllegalArgumentException.
     * 2. Ghi lỗi vào log để backend dễ kiểm tra.
     * 3. Lấy message từ exception.
     * 4. Tạo ApiResponse lỗi với status code 400.
     * 5. Trả lỗi BAD_REQUEST về frontend.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("Illegal Argument Exception: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Xử lý các lỗi còn lại chưa được bắt riêng.
     * Method này đóng vai trò fallback để backend không trả lỗi thô trực tiếp về frontend.
     *
     * Pseudo code:
     * 1. Bắt tất cả exception chưa có handler riêng.
     * 2. Ghi lỗi vào log để developer kiểm tra nguyên nhân.
     * 3. Tạo ApiResponse lỗi với status code 500.
     * 4. Trả lỗi INTERNAL_SERVER_ERROR về frontend.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex) {
        log.error("An unexpected error occurred: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred: " + ex.getMessage()));
    }
}