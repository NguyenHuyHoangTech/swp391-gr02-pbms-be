package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO representing an OTP verification request.
 *               Carries email, OTP code, and verification purpose.
 * @Dependencies:
 * - Validation annotations (jakarta.validation.constraints)
 */
@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "OTP Code is required")
    private String otpCode;

    @NotBlank(message = "Purpose is required")
    private String purpose;
}

