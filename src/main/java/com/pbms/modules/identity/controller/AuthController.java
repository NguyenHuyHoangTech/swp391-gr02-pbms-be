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
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Success", "Success"));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request.getEmail(), request.getPurpose());
        return ResponseEntity.ok(ApiResponse.success("Success", "CODE OTP"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Check OTP status"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Success"));
    }

    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Click on Google to listen"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Success", "CODE OTP"));
    }

    @PostMapping("/verify-forgot-password")
    public ResponseEntity<ApiResponse<String>> verifyForgotPassword(@Valid @RequestBody VerifyForgotPasswordRequest request) {
        String resetToken = authService.verifyForgotPasswordOtp(request.getEmail(), request.getOtpCode());
        return ResponseEntity.ok(ApiResponse.success(resetToken, "Check that OTP can hear you. Please feel free to comment."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            Authentication authentication,
            @Valid @RequestBody ResetPasswordRequest request) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.success(null, "It's quite different from other people."));
        }

        String email = authentication.getName();
        authService.resetPassword(email, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Success", "It's pretty cool and you can listen to it. Have fun."));
    }

    @PostMapping("/set-password")
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

    @PostMapping("/link-google")
    public ResponseEntity<ApiResponse<String>> linkGoogle(
            Authentication authentication,
            @Valid @RequestBody GoogleAuthRequest request) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();
        authService.linkGoogle(email, request);
        return ResponseEntity.ok(ApiResponse.success("Success", "Success"));
    }

    @GetMapping("/test-get-otp")
    public ResponseEntity<String> getOtpForTest(@RequestParam String email, @RequestParam String purpose) {
        return ResponseEntity.ok(authService.getOtpForTest(email, purpose));
    }
}


