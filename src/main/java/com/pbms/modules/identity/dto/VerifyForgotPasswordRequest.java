package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO representing a forgot password OTP verification request.
 *               Carries email and OTP code for password recovery validation.
 * @Dependencies:
 * - Validation annotations (jakarta.validation.constraints)
 */
@Data
public class VerifyForgotPasswordRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String otpCode;
}

