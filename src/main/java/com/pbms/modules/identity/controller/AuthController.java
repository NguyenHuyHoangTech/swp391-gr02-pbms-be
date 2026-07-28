/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC XỬ LÝ REQUEST CỦA HỆ THỐNG (KÈM MINH CHỨNG CODE)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO BỘ ĐIỀU PHỐI VÀ TIÊM PHỤ THUỘC (DEPENDENCY INJECTION)
 * - Minh chứng 1: Khai báo @RestController báo cho Spring Boot biết class này là một Web API.
 * - Minh chứng 2: Khai báo @RequiredArgsConstructor (hoặc Constructor) để Spring Boot 
 *   tự động tiêm các Service vào biến cục bộ (Constructor Injection).
 * 
 * BƯỚC 2: TIẾP NHẬN REQUEST TỪ CLIENT (DISPATCHER SERVLET & HANDLER MAPPING)
 * - Minh chứng 1: Khai báo @RequestMapping ở đầu class quy định Gốc của URL.
 * - Minh chứng 2: Các hàm được đánh dấu @PostMapping, @GetMapping, @PutMapping 
 *   là bằng chứng cho việc HandlerMapping sẽ định tuyến chính xác mọi Request vào đúng hàm.
 * 
 * BƯỚC 3: RÀNG BUỘC VÀ CHUYỂN ĐỔI DỮ LIỆU (DESERIALIZATION)
 * - Minh chứng: Các tham số @RequestBody, @PathVariable, @RequestParam kích hoạt 
 *   thư viện Jackson đọc chuỗi văn bản JSON thành dạng Object Java.
 * 
 * @author Phạm Anh Tuấn
 * @created 03/05/2026
 */
package com.pbms.modules.identity.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.identity.dto.*;
import com.pbms.modules.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    /**
     * =========================================================================
     * NGHIỆP VỤ: REGISTER
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho register.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Success", "Success"));
    }

    @PostMapping("/send-otp")
    /**
     * =========================================================================
     * NGHIỆP VỤ: SENDOTP
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho sendOtp.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request.getEmail(), request.getPurpose());
        return ResponseEntity.ok(ApiResponse.success("Success", "OTP sent successfully"));
    }

    @PostMapping("/verify-otp")
    /**
     * =========================================================================
     * NGHIỆP VỤ: VERIFYOTP
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho verifyOtp.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response, "OTP verified successfully"));
    }

    @PostMapping("/login")
    /**
     * =========================================================================
     * NGHIỆP VỤ: LOGIN
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho login.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Success"));
    }

    @PostMapping("/login/google")
    /**
     * =========================================================================
     * NGHIỆP VỤ: LOGINGOOGLE
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho loginGoogle.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<AuthResponse>> loginGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Google login successful"));
    }

    @PostMapping("/forgot-password")
    /**
     * =========================================================================
     * NGHIỆP VỤ: FORGOTPASSWORD
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho forgotPassword.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Success", "OTP for password reset sent"));
    }

    @PostMapping("/verify-forgot-password")
    /**
     * =========================================================================
     * NGHIỆP VỤ: VERIFYFORGOTPASSWORD
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho verifyForgotPassword.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> verifyForgotPassword(@Valid @RequestBody VerifyForgotPasswordRequest request) {
        String resetToken = authService.verifyForgotPasswordOtp(request.getEmail(), request.getOtpCode());
        return ResponseEntity.ok(ApiResponse.success(resetToken, "OTP verified successfully"));
    }

    @PostMapping("/reset-password")
    /**
     * =========================================================================
     * NGHIỆP VỤ: RESETPASSWORD
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho resetPassword.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> resetPassword(
            Authentication authentication,
            @Valid @RequestBody ResetPasswordRequest request) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.success(null, "Passwords do not match"));
        }

        String email = authentication.getName();
        authService.resetPassword(email, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Success", "Password reset successfully"));
    }

    @PostMapping("/set-password")
    /**
     * =========================================================================
     * NGHIỆP VỤ: SETPASSWORD
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho setPassword.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> setPassword(
            Authentication authentication,
            @Valid @RequestBody SetPasswordRequest request) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String email = authentication.getName();
        authService.setPassword(email, request);
        return ResponseEntity.ok(ApiResponse.success("Success", "Success"));
    }

    @PutMapping("/profile")
    /**
     * =========================================================================
     * NGHIỆP VỤ: UPDATEPROFILE
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho updateProfile.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<ApiResponse<String>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String email = authentication.getName();
        authService.updateProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success("Success", "Profile updated successfully"));
    }



    @GetMapping("/test-get-otp")
    /**
     * =========================================================================
     * NGHIỆP VỤ: GETOTPFORTEST
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho getOtpForTest.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public ResponseEntity<String> getOtpForTest(@RequestParam String email, @RequestParam String purpose) {
        return ResponseEntity.ok(authService.getOtpForTest(email, purpose));
    }
}


