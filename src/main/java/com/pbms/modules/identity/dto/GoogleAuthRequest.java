package com.pbms.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: Pham Anh Tuan
 * @Date: 2026-07-02
 * @Description: DTO representing a Google authentication request.
 *               Carries the Google ID token for login or account linking.
 * @Dependencies:
 * - Validation annotations (jakarta.validation.constraints)
 */
@Data
public class GoogleAuthRequest {
    @NotBlank(message = "Google ID Token is required")
    private String googleIdToken;
}

