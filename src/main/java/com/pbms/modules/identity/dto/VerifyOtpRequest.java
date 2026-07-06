package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "OTP Code is required")
    private String otpCode;

    @NotBlank(message = "Purpose is required")
    private String purpose; // REGISTER, FORGOT_PASSWORD
}

