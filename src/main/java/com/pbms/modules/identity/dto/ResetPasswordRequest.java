package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    @jakarta.validation.constraints.Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!_]).{8,20}$",
        message = "Password must be 8-20 characters long, and contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}

