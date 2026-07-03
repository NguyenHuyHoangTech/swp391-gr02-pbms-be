package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO representing a forgot password request.
 *               Carries the email used to start password recovery.
 * @Dependencies:
 * - Validation annotations (jakarta.validation.constraints)
 */
@Data
public class ForgotPasswordRequest {
    @NotBlank @Email
    private String email;
}

