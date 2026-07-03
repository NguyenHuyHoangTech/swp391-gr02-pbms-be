package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO representing an OTP sending request.
 *               Carries the target email and OTP purpose.
 * @Dependencies:
 * - Validation annotations (jakarta.validation.constraints)
 */
@Data
public class SendOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String purpose = "REGISTER";
}

