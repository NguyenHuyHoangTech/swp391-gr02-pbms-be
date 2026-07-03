package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyForgotPasswordRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String otpCode;
}

